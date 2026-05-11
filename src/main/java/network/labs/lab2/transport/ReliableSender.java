package network.labs.lab2.transport;

import network.labs.lab2.common.Config;
import network.labs.lab2.common.NetworkUtils;
import network.labs.lab2.common.UdpPacket;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Компонент надёжной отправки данных поверх UDP для лабораторной работы №2.
 *
 * Реализует механизмы, эмулирующие надёжность транспортного уровня:
 * - Скользящее окно (sliding window): отправка до WINDOW_SIZE пакетов без ожидания ACK.
 * - Кумулятивные подтверждения: обработка ACK, сдвигающих окно вперёд.
 * - Ретрансмиссия при потере: повторная отправка неподтверждённых пакетов по таймауту.
 * - Детект обрыва сессии: если самый старый пакет исчерпал лимит попыток → разрыв.
 * - Сигнал завершения (FIN): уведомление получателя об окончании потока.
 *
 * Класс работает в одном потоке, использует блокирующий I/O с настраиваемыми таймаутами.
 * Все операции синхронны, что соответствует требованию "сервер в одном потоке".
 */
public final class ReliableSender {

    // ========================================================================
    // Поля состояния
    // ========================================================================

    /** Сокет для отправки UDP-датаграмм и приёма ACK */
    private final DatagramSocket sock;

    /** Адрес удалённой стороны (получателя данных) */
    private final InetSocketAddress peer;

    /**
     * Буфер для чтения данных из входного потока.
     * Размер: PAYLOAD_SIZE = 1465 байт — максимум полезных данных в одном пакете.
     */
    private final byte[] readBuf = new byte[Config.PAYLOAD_SIZE];

    /**
     * Буфер для приёма ACK-пакетов.
     * Размер 64 байта с запасом: ACK содержит только 7-байтовый заголовок.
     */
    private final byte[] ackBuf = new byte[64];

    /** Общее количество отправленных полезных байт */
    private long sent = 0;

    /** Общее количество подтверждённых байт (для статистики) */
    private long acked = 0;

    /** Счётчик ретрансмиссий (для отладки/статистики) */
    private long retx = 0;

    private boolean progressFinished = false;

    /**
     * Создаёт отправитель для работы с указанным сокетом и удалённым адресом.
     *
     * @param sock сокет для отправки датаграмм
     * @param peer адрес получателя данных
     */
    public ReliableSender(DatagramSocket sock, InetSocketAddress peer) {
        this.sock = sock;
        this.peer = peer;
    }

    /**
     * Отправляет поток данных через сокет с обеспечением надёжности.
     *
     * Алгоритм работы:
     * 1. Инициализирует таймаут сокета и локальные счётчики.
     * 2. Создаёт очередь окна (ArrayDeque) размером WINDOW_SIZE.
     * 3. В цикле:
     *    - fillWindow(): заполняет окно новыми пакетами из InputStream.
     *    - processAcks(): обрабатывает пришедшие ACK, сдвигает окно.
     *    - checkTimeouts(): ретрансмитит неподтверждённые пакеты, детектит обрыв.
     *    - printProgress(): выводит статистику передачи каждые 1 МБ.
     * 4. После отправки всех данных отправляет пакет с флагом FIN.
     *
     * @param in входной поток с данными для отправки
     * @param total ожидаемый размер данных в байтах
     * @throws IOException при ошибке сети или обрыве сессии
     */
    public void sendStream(InputStream in, long total) throws IOException {
        int old = sock.getSoTimeout();
        sock.setSoTimeout(Config.SOCKET_TIMEOUT_MS);
        try {
            Queue<Pending> win = new ArrayDeque<>(Config.WINDOW_SIZE);
            int seq = 0;
            long start = System.currentTimeMillis();

            // Цикл продолжается, пока есть неотправленные данные или неподтверждённые пакеты
            while (sent < total || !win.isEmpty()) {
                fillWindow(in, win, total, seq);
                seq = processAcks(win);
                checkTimeouts(win);
                printProgress(sent, total, start);
            }

            // Сигнал завершения передачи
            byte[] fin = UdpPacket.fin(seq).toBytes();
            sock.send(new DatagramPacket(fin, fin.length, peer));
        } finally {
            sock.setSoTimeout(old);
        }
    }

    /**
     * Заполняет окно новыми пакетами, пока есть место и данные.
     *
     * Для каждого пакета:
     * 1. Читает до PAYLOAD_SIZE байт из InputStream.
     * 2. Создаёт UdpPacket с флагом DATA и текущим seq.
     * 3. Сериализует и отправляет через сокет.
     * 4. Добавляет в окно объект Pending для отслеживания таймаута.
     *
     * @param in входной поток с данными
     * @param win очередь окна (неподтверждённые пакеты)
     * @param total общий размер данных
     * @param seq начальный порядковый номер для этого вызова
     * @throws IOException при ошибке чтения или отправки
     */
    private void fillWindow(InputStream in, Queue<Pending> win, long total, int seq) throws IOException {
        while (win.size() < Config.WINDOW_SIZE && sent < total) {
            int toRead = (int) Math.min(Config.PAYLOAD_SIZE, total - sent);
            int read = in.read(readBuf, 0, toRead);
            if (read <= 0) {
                break; // Конец потока
            }
            UdpPacket pkt = UdpPacket.data(seq, readBuf, 0, read);
            byte[] bytes = pkt.toBytes();
            sock.send(new DatagramPacket(bytes, bytes.length, peer));
            win.add(new Pending(seq, bytes, read, System.currentTimeMillis()));
            seq++;
            sent += read;
        }
    }

    /**
     * Обрабатывает пришедшие ACK-пакеты и сдвигает окно.
     *
     * Алгоритм:
     * 1. Пытается принять один пакет с таймаутом.
     * 2. Если это ACK: извлекает номер подтверждённого пакета (ackNum).
     * 3. Удаляет из окна все пакеты с seq <= ackNum (кумулятивное подтверждение).
     * 4. Возвращает следующий ожидаемый seq для fillWindow().
     *
     * @param win очередь окна (неподтверждённые пакеты)
     * @return следующий порядковый номер для отправки
     * @throws IOException при ошибке приёма
     */
    private int processAcks(Queue<Pending> win) throws IOException {
        int last = win.isEmpty() ? 0 : win.peek().seq - 1;
        try {
            DatagramPacket dp = new DatagramPacket(ackBuf, ackBuf.length);
            sock.receive(dp);
            UdpPacket p = UdpPacket.fromBytes(dp.getData(), dp.getLength());
            if (p.isAck()) {
                int ackNum = p.ack();
                // Удаляем все подтверждённые пакеты из начала окна
                while (!win.isEmpty() && win.peek().seq <= ackNum) {
                    Pending x = win.poll();
                    acked += x.size;
                    last = Math.max(last, x.seq);
                }
            }
        } catch (SocketTimeoutException ignored) {
            // Таймаут — нормальная ситуация, продолжаем цикл
        }
        return last + 1;
    }

    /**
     * Проверяет таймауты неподтверждённых пакетов и выполняет ретрансмиссию.
     *
     * Алгоритм:
     * 1. Для каждого пакета в окне: если прошло больше ACK_TIMEOUT_MS с момента
     *    отправки и число попыток < лимита → переотправляем пакет.
     * 2. Если самый старый пакет в окне исчерпал лимит попыток → выбрасываем
     *    SocketTimeoutException для сигнала обрыва сессии.
     *
     * @param win очередь окна (неподтверждённые пакеты)
     * @throws IOException при ошибке отправки или обнаружении обрыва
     */
    private void checkTimeouts(Queue<Pending> win) throws IOException {
        long now = System.currentTimeMillis();
        for (Pending p : win) {
            // Ретрансмиссия: если таймаут истёк и лимит попыток не исчерпан
            if (now - p.sentAt > Config.ACK_TIMEOUT_MS && p.retries < Config.MAX_CONSECUTIVE_DROPS) {
                sock.send(new DatagramPacket(p.bytes, p.bytes.length, peer));
                p.sentAt = now;
                p.retries++;
                retx++;
            }
        }
        // Детект обрыва: если самый старый пакет исчерпал попытки
        if (!win.isEmpty() && win.peek().retries >= Config.MAX_CONSECUTIVE_DROPS) {
            int attempts = win.peek().retries;
            throw new SocketTimeoutException(
                    "Сессия разорвана: нет ACK после " + attempts +
                            " попыток (таймаут " + Config.ACK_TIMEOUT_MS + " мс)"
            );
        }
    }

    /**
     * Выводит прогресс передачи в консоль.
     *
     * Особенности:
     * - Обновление только при кратности 1 МБ (чтобы не засорять вывод).
     * - Защита от деления на 0 при расчёте битрейта.
     * - Возврат каретки (\r) для обновления одной строки.
     *
     * @param cur текущее количество отправленных байт
     * @param tot ожидаемый размер в байтах
     * @param start время начала передачи (в миллисекундах)
     */
    private void printProgress(long cur, long tot, long start) {
        if (cur >= tot && !progressFinished) {
            long elapsed = Math.max(System.currentTimeMillis() - start, 1);
            double mbps = NetworkUtils.calcSpeedMbps(cur, elapsed);
            System.out.printf("Отправлено: 100%% | %.1f Мбит/с | Ретрансмиссий: %d%n", mbps, retx);
            progressFinished = true;
            return;
        }
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed <= 0 || cur % (1024 * 1024) != 0) return;
        double mbps = NetworkUtils.calcSpeedMbps(cur, elapsed);
        System.out.printf("\rОтправлено: %3d%% | %.1f Мбит/с | Ретрансмиссий: %d",
                (int) (cur * 100 / tot), mbps, retx);
    }

    // ========================================================================
    // Внутренний класс для отслеживания отправленных пакетов
    // ========================================================================

    /**
     * Вспомогательный класс для хранения состояния отправленного пакета.
     * Используется в очереди скользящего окна для управления таймаутами и ретрансмиссиями.
     */
    private static class Pending {
        /** Порядковый номер пакета */
        final int seq;
        /** Сериализованные байты пакета (для повторной отправки) */
        final byte[] bytes;
        /** Размер полезных данных в пакете */
        final int size;
        /** Время последней отправки (в миллисекундах) */
        long sentAt;
        /** Количество попыток отправки */
        int retries;

        /**
         * Создаёт запись для отслеживания пакета.
         *
         * @param s порядковый номер
         * @param b сериализованные байты
         * @param sz размер полезных данных
         * @param t время отправки
         */
        Pending(int s, byte[] b, int sz, long t) {
            this.seq = s;
            this.bytes = b;
            this.size = sz;
            this.sentAt = t;
            this.retries = 0;
        }
    }
}