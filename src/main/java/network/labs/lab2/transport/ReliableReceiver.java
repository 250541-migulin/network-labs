package network.labs.lab2.transport;

import network.labs.lab2.common.Config;
import network.labs.lab2.common.UdpPacket;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.TreeMap;

/**
 * Компонент надёжного приёма данных поверх UDP для лабораторной работы №2.
 *
 * Реализует механизмы, эмулирующие надёжность транспортного уровня:
 * - Буферизация внеочередных пакетов (reordering) с записью в поток строго по порядку.
 * - Кумулятивные подтверждения (ACK): один ответ на каждые ACK_INTERVAL пакетов.
 * - Детект обрыва сессии по таймауту поступления пакетов.
 * - Обработка сигнала завершения передачи (флаг FIN).
 *
 * Класс работает в одном потоке, использует блокирующий I/O с настраиваемыми таймаутами.
 * Все операции синхронны, что соответствует требованию "сервер в одном потоке".
 */
public final class ReliableReceiver {

    // ========================================================================
    // Поля состояния
    // ========================================================================

    /** Сокет для приёма UDP-датаграмм */
    private final DatagramSocket sock;

    /** Адрес удалённой стороны (отправителя данных) */
    private final InetSocketAddress peer;

    /**
     * Буфер для приёма сырых датаграмм.
     * Размер: PAYLOAD_SIZE + HEADER_SIZE = 1465 + 7 = 1472 байта.
     * Это максимум данных, которые могут прийти в одном пакете нашего протокола.
     */
    private final byte[] recvBuf = new byte[Config.PAYLOAD_SIZE + Config.HEADER_SIZE];

    /**
     * Буфер для внеочередных пакетов.
     * Ключ: порядковый номер пакета (seq), значение: полезные данные.
     * Используется TreeMap для автоматической сортировки по ключу.
     */
    private final TreeMap<Integer, byte[]> reorder = new TreeMap<>();

    /** Порядковый номер следующего ожидаемого пакета (начинается с 0) */
    private int nextSeq = 0;

    /** Общее количество принятых полезных байт */
    private int received = 0;

    /** Счётчик пакетов, пришедших вне очереди (для отладки/статистики) */
    private int ooo = 0;

    /** Порядковый номер последнего отправленного кумулятивного ACK */
    private int lastAckSent = -1;

    /**
     * Создаёт приёмник для работы с указанным сокетом и удалённым адресом.
     *
     * @param sock сокет для приёма датаграмм
     * @param peer адрес отправителя данных
     */
    public ReliableReceiver(DatagramSocket sock, InetSocketAddress peer) {
        this.sock = sock;
        this.peer = peer;
    }

    /**
     * Принимает поток данных и записывает его в указанный OutputStream.
     *
     * Алгоритм работы:
     * 1. Инициализирует таймаут сокета и счётчики.
     * 2. В цикле читает пакеты до достижения expected байт или получения FIN.
     * 3. Детектит обрыв по превышению MAX_CONSECUTIVE_DROPS подряд таймаутов.
     * 4. Для пакетов по порядку: записывает в поток, сдвигает nextSeq, шлёт кумулятивный ACK.
     * 5. Для внеочередных пакетов: буферизует в reorder, шлёт немедленный ACK.
     * 6. После записи пакета по порядку проверяет буфер reorder на наличие следующих по порядку.
     * 7. Периодически выводит прогресс передачи (каждый 1 МБ).
     *
     * @param out поток для записи принятых данных
     * @param expected ожидаемый размер данных в байтах
     * @return фактически принятое количество байт
     * @throws IOException при ошибке сети или обрыве сессии
     */
    public long receiveStream(OutputStream out, long expected) throws IOException {
        int old = sock.getSoTimeout();
        sock.setSoTimeout(Config.SOCKET_TIMEOUT_MS);
        try {
            long start = System.currentTimeMillis();
            int dropCounter = 0;
            int localLastAckSent = -1; // Локальная копия для отслеживания отправленных ACK

            while (received < expected) {
                // Детект обрыва: если подряд не получено ни одного пакета
                if (++dropCounter >= Config.MAX_CONSECUTIVE_DROPS) {
                    throw new SocketTimeoutException("Сессия разорвана: нет пакетов от отправителя");
                }

                UdpPacket pkt = readPacket();
                if (pkt == null) {
                    continue; // Таймаут — продолжаем цикл
                }

                dropCounter = 0; // Сброс счётчика при получении любого валидного пакета

                // Обработка сигнала завершения передачи
                if (pkt.isFin()) {
                    break;
                }

                // Пропускаем не-данные или устаревшие пакеты
                if (!pkt.isData() || pkt.seq() < nextSeq) {
                    continue;
                }

                // Внеочередной пакет: буферизация + немедленный ACK
                if (pkt.seq() != nextSeq) {
                    sendAck(pkt.seq());
                    reorder.put(pkt.seq(), pkt.dataUnsafe());
                    ooo++;
                    continue;
                }

                // Пакет по порядку: запись в поток и обновление состояния
                out.write(pkt.dataUnsafe(), 0, pkt.dataLength());
                received += pkt.dataLength();
                nextSeq++;
                flushBuffer(out);

                // Отправка кумулятивного ACK: каждые ACK_INTERVAL пакетов или в конце
                if ((nextSeq - 1 - localLastAckSent) >= Config.ACK_INTERVAL || received >= expected) {
                    sendAck(nextSeq - 1);
                    localLastAckSent = nextSeq - 1;
                }

                printProgress(received, expected, start);
            }
        } finally {
            sock.setSoTimeout(old);
        }
        return received;
    }

    /**
     * Читает одну UDP-датаграмму и десериализует её в UdpPacket.
     *
     * @return объект UdpPacket или null при таймауте
     * @throws IOException при ошибке десериализации
     */
    private UdpPacket readPacket() throws IOException {
        try {
            DatagramPacket dp = new DatagramPacket(recvBuf, recvBuf.length);
            sock.receive(dp);
            return UdpPacket.fromBytes(dp.getData(), dp.getLength());
        } catch (SocketTimeoutException e) {
            return null;
        }
    }

    /**
     * Отправляет подтверждение (ACK) для указанного порядкового номера.
     *
     * @param seq номер пакета, который подтверждается
     * @throws IOException при ошибке отправки
     */
    private void sendAck(int seq) throws IOException {
        byte[] bytes = UdpPacket.ack(seq).toBytes();
        sock.send(new DatagramPacket(bytes, bytes.length, peer));
    }

    /**
     * Записывает в выходной поток все пакеты из буфера reorder,
     * которые идут строго по порядку начиная с nextSeq.
     *
     * После записи каждого пакета проверяет, не пора ли отправить
     * кумулятивный ACK.
     *
     * @param out поток для записи данных
     * @throws IOException при ошибке записи
     */
    private void flushBuffer(OutputStream out) throws IOException {
        while (!reorder.isEmpty() && reorder.firstKey() == nextSeq) {
            byte[] data = reorder.pollFirstEntry().getValue();
            out.write(data);
            received += data.length;
            nextSeq++;

            // Проверка необходимости отправки кумулятивного ACK
            if ((nextSeq - 1 - lastAckSent) >= Config.ACK_INTERVAL) {
                sendAck(nextSeq - 1);
                lastAckSent = nextSeq - 1;
            }
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
     * @param cur текущее количество принятых байт
     * @param tot ожидаемый размер в байтах
     * @param start время начала передачи (в миллисекундах)
     */
    private void printProgress(long cur, long tot, long start) {
        long elapsed = System.currentTimeMillis() - start;
        // Обновляем прогресс только каждые 1 МБ и если прошло достаточно времени
        if (elapsed <= 0 || cur % (1024 * 1024) != 0) {
            return;
        }
        double mbps = (cur * 8.0) / elapsed;
        System.out.printf("\rПринято: %3d%% | %.1f Мбит/с | Вне очереди: %d",
                (int) (cur * 100 / tot), mbps, ooo);
        if (cur >= tot) {
            System.out.println();
        }
    }
}