package network.labs.lab2.server;

import network.labs.lab2.common.Config;
import network.labs.lab2.common.UdpPacket;
import network.labs.lab2.transport.ReliableReceiver;
import network.labs.lab2.transport.ReliableSender;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * UDP-сервер для лабораторной работы №2.
 *
 * Реализует последовательную обработку команд клиента в одном потоке:
 * - ECHO, TIME, CLOSE: простые текстовые команды с ответом.
 * - UPLOAD, DOWNLOAD: передача файлов с надёжностью через ReliableReceiver/Sender.
 *
 * Особенности реализации:
 * - Работает в одном потоке (while + receive), без многопоточности.
 * - Поддерживает докачку: если тот же клиент запрашивает тот же файл,
 *   сервер возвращает смещение (offset) для продолжения с места обрыва.
 * - Логирует события подключения и тип операции для наглядности демонстрации.
 * - Обрабатывает ошибки локально, не прерывая основной цикл сервера.
 */
public final class UdpServer {

    // ========================================================================
    // Поля состояния сервера
    // ========================================================================

    /** Сокет для приёма и отправки UDP-датаграмм */
    private final DatagramSocket sock;

    /** Адрес последнего клиента, работавшего с файлом (для докачки) */
    private InetSocketAddress lastClient;

    /** Имя последнего файла, с которым работал клиент (для докачки) */
    private String lastFile;

    /**
     * Буфер для приёма сырых датаграмм.
     * Размер 1536 байт: запас поверх максимального размера нашего пакета (1472).
     */
    private final byte[] buf = new byte[1536];

    /** IP-адрес последнего подключившегося клиента (для логирования) */
    private String lastSeenIP = null;

    /**
     * Создаёт серверный сокет и инициализирует параметры.
     *
     * @param port порт для прослушивания входящих UDP-датаграмм
     * @throws IOException если не удалось создать или настроить сокет
     */
    public UdpServer(int port) throws IOException {
        sock = new DatagramSocket(port);
        // Увеличенные буферы для повышения пропускной способности в LAN
        sock.setReceiveBufferSize(Config.SOCK_BUF_SIZE);
        sock.setSendBufferSize(Config.SOCK_BUF_SIZE);
        // Короткий таймаут для основного цикла: быстрая проверка без блокировки
        sock.setSoTimeout(50);
        System.out.println("Сервер UDP запущен на порту: " + port);
    }

    /**
     * Запускает основной цикл обработки входящих датаграмм.
     *
     * Алгоритм работы:
     * 1. В бесконечном цикле пытается принять пакет с таймаутом 50 мс.
     * 2. При получении пакета:
     *    - Извлекает адрес отправителя для логирования "подключения".
     *    - Десериализует пакет в UdpPacket.
     *    - Если это командный пакет (FLAG_CMD) → передаёт в handleCmd().
     * 3. При таймауте или ошибке десериализации — продолжает цикл.
     *
     * Сервер работает в одном потоке, не использует многопоточность или select/poll.
     *
     * @throws IOException при неустранимой ошибке сокета
     */
    public void start() throws IOException {
        while (true) {
            try {
                DatagramPacket dp = new DatagramPacket(buf, buf.length);
                sock.receive(dp);
                InetSocketAddress peer = new InetSocketAddress(dp.getAddress(), dp.getPort());

                // Логирование "подключения": первый пакет с нового IP-адреса
                String ip = peer.getAddress().toString();
                if (!ip.equals(lastSeenIP)) {
                    System.out.println("Клиент подключился: " + ip);
                    lastSeenIP = ip;
                }

                UdpPacket pkt = UdpPacket.fromBytes(dp.getData(), dp.getLength());
                if (pkt.isCmd()) {
                    handleCmd(peer, pkt);
                }
            } catch (SocketTimeoutException ignored) {
                // Нормальная ситуация: таймаут для неблокирующей проверки
            } catch (IOException e) {
                // Логирование ошибки без прерывания основного цикла
                System.err.println("Ошибка приёма пакета: " + e.getMessage());
            }
        }
    }

    /**
     * Обрабатывает текстовую команду от клиента.
     *
     * Алгоритм:
     * 1. Десериализует полезные данные пакета в строку (UTF-8).
     * 2. Разделяет на команду и аргумент (пробел как разделитель).
     * 3. Приводит команду к верхнему регистру для регистронезависимого сравнения.
     * 4. Передаёт управление соответствующему обработчику через switch.
     *
     * @param peer адрес клиента, отправившего команду
     * @param pkt десериализованный пакет с командой
     * @throws IOException при ошибке отправки ответа
     */
    private void handleCmd(InetSocketAddress peer, UdpPacket pkt) throws IOException {
        String raw = new String(pkt.dataUnsafe(), 0, pkt.dataLength(), StandardCharsets.UTF_8).trim();
        String[] parts = raw.split("\\s+", 2);
        String cmd = parts[0].toUpperCase();

        switch (cmd) {
            case "ECHO":
                reply(peer, "Эхо: " + (parts.length > 1 ? parts[1] : ""));
                break;
            case "TIME":
                reply(peer, "Время: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                break;
            case "CLOSE":
                lastClient = null;
                lastFile = null;
                reply(peer, "Сессия закрыта");
                break;
            case "UPLOAD":
                if (parts.length < 2) {
                    reply(peer, "ERROR: укажите имя файла");
                } else {
                    handleUpload(peer, parts);
                }
                break;
            case "DOWNLOAD":
                if (parts.length < 2) {
                    reply(peer, "ERROR: укажите имя файла");
                } else {
                    handleDownload(peer, parts);
                }
                break;
            default:
                reply(peer, "ERROR: Неизвестная команда: " + cmd);
        }
    }

    /**
     * Отправляет текстовый ответ клиенту в виде командного UDP-пакета.
     *
     * @param peer адрес получателя
     * @param text текст ответа (кодируется в UTF-8)
     * @throws IOException при ошибке отправки
     */
    private void reply(InetSocketAddress peer, String text) throws IOException {
        byte[] b = UdpPacket.cmd(text).toBytes();
        sock.send(new DatagramPacket(b, b.length, peer));
    }

    /**
     * Обрабатывает команду UPLOAD: приём файла от клиента с поддержкой докачки.
     *
     * Алгоритм:
     * 1. Проверяет наличие аргумента (имя файла).
     * 2. Определяет, является ли это докачкой:
     *    - тот же клиент (peer.equals(lastClient))
     *    - тот же файл (fname.equals(lastFile))
     *    - файл уже существует на сервере
     * 3. Если докачка: вычисляет offset = размер существующего файла.
     *    Иначе: offset = 0 (новая загрузка).
     * 4. Отправляет клиенту "OK <offset>" и ждёт размер остатка.
     * 5. Если остаток = 0 → файл уже загружен полностью.
     * 6. Иначе: отправляет "READY" и запускает ReliableReceiver для приёма.
     * 7. После завершения: выводит битрейт, отправляет финальный ответ,
     *    обновляет lastClient/lastFile для поддержки последующей докачки.
     *
     * @param peer адрес клиента
     * @param parts массив [команда, имя_файла]
     * @throws IOException при ошибке сети или файловой системы
     */
    private void handleUpload(InetSocketAddress peer, String[] parts) throws IOException {
        if (parts.length < 2) {
            reply(peer, "ERROR: укажите имя файла");
            return;
        }
        String fname = parts[1];
        Path tgt = Config.DST_DIR.resolve(fname);

        // Проверка условия докачки из ТЗ: тот же клиент + тот же файл + файл существует
        boolean isResume = peer.equals(lastClient) && fname.equals(lastFile) && Files.exists(tgt);
        long off = isResume ? Files.size(tgt) : 0;

        System.out.println(isResume
                ? "Докачка (продолжение): " + fname + " с " + off + " байт"
                : "Новая загрузка: " + fname);

        reply(peer, "OK " + off);
        String sz = readCmd(peer);
        if (sz == null) {
            return;
        }
        long rem = Long.parseLong(sz.trim());
        if (rem <= 0) {
            reply(peer, "Файл уже загружен");
            return;
        }
        reply(peer, "READY");

        System.out.println("Приём данных...");
        long t0 = System.currentTimeMillis();
        try (var out = Files.newOutputStream(tgt, StandardOpenOption.CREATE,
                off > 0 ? StandardOpenOption.APPEND : StandardOpenOption.WRITE)) {
            new ReliableReceiver(sock, peer).receiveStream(out, rem);
        }
        long elapsed = Math.max(System.currentTimeMillis() - t0, 1);
        System.out.printf("Принято: %.1f Мбит/с\n", (rem * 8.0) / elapsed);
        reply(peer, "Файл загружен: " + fname);

        // Обновляем состояние для поддержки последующей докачки
        lastClient = peer;
        lastFile = fname;
    }

    /**
     * Обрабатывает команду DOWNLOAD: отправка файла клиенту с поддержкой докачки.
     *
     * Алгоритм:
     * 1. Проверяет наличие аргумента (имя файла) и существование файла на сервере.
     * 2. Определяет, является ли это докачкой: тот же клиент + тот же файл.
     * 3. Отправляет клиенту "OK" и ждёт смещение (offset) от клиента.
     * 4. Вычисляет остаток: rem = размер_файла - offset.
     * 5. Если остаток = 0 → файл уже актуален у клиента.
     * 6. Иначе: запускает ReliableSender для отправки остатка.
     * 7. После завершения: выводит битрейт, отправляет финальный ответ,
     *    обновляет lastClient/lastFile для поддержки последующей докачки.
     *
     * @param peer адрес клиента
     * @param parts массив [команда, имя_файла]
     * @throws IOException при ошибке сети или файловой системы
     */
    private void handleDownload(InetSocketAddress peer, String[] parts) throws IOException {
        if (parts.length < 2) {
            reply(peer, "ERROR: укажите имя файла");
            return;
        }
        String fname = parts[1];
        Path src = Config.SRC_DIR.resolve(fname);
        if (!Files.exists(src)) {
            reply(peer, "ERROR: файл не найден: " + fname);
            return;
        }

        boolean isResume = peer.equals(lastClient) && fname.equals(lastFile);
        System.out.println(isResume
                ? "Докачка (отдача остатка): " + fname
                : "Новая отдача: " + fname);

        reply(peer, "OK");
        String off = readCmd(peer);
        long skip = off != null ? Long.parseLong(off.trim()) : 0;
        long rem = Files.size(src) - skip;
        reply(peer, String.valueOf(rem));
        if (rem <= 0) {
            reply(peer, "Файл уже актуален");
            return;
        }

        System.out.println("Отправка данных...");
        long t0 = System.currentTimeMillis();
        try (var in = Files.newInputStream(src)) {
            in.skipNBytes(skip);
            new ReliableSender(sock, peer).sendStream(in, rem);
        }
        long elapsed = Math.max(System.currentTimeMillis() - t0, 1);
        System.out.printf("Отправлено: %.1f Мбит/с\n", (rem * 8.0) / elapsed);
        reply(peer, "Файл отправлен: " + fname);

        // Обновляем состояние для поддержки последующей докачки
        lastClient = peer;
        lastFile = fname;
    }

    /**
     * Ожидает и возвращает текстовую команду от указанного клиента.
     *
     * Используется в рукопожатии UPLOAD/DOWNLOAD для получения от клиента
     * размера остатка или смещения. Отличается от receiveCmd() в клиенте тем,
     * что фильтрует пакеты по адресу отправителя (expected).
     *
     * Особенности:
     * - Временное увеличение таймаута сокета до 2000 мс для ожидания ответа.
     * - Внутренний цикл с интервалом 200 мс для реактивности.
     * - Игнорирует пакеты не от ожидаемого клиента или не типа CMD.
     *
     * @param expected адрес клиента, от которого ожидается ответ
     * @return текст команды или null при таймауте
     * @throws IOException при ошибке сети
     */
    private String readCmd(InetSocketAddress expected) throws IOException {
        int old = sock.getSoTimeout();
        sock.setSoTimeout(2000);
        try {
            long deadline = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < deadline) {
                try {
                    sock.setSoTimeout(200);
                    DatagramPacket dp = new DatagramPacket(buf, buf.length);
                    sock.receive(dp);
                    // Игнорируем пакеты не от ожидаемого клиента
                    if (!dp.getAddress().equals(expected.getAddress())) {
                        continue;
                    }
                    UdpPacket p = UdpPacket.fromBytes(dp.getData(), dp.getLength());
                    // Принимаем только командные пакеты
                    if (p.isCmd()) {
                        return new String(p.dataUnsafe(), 0, p.dataLength(), StandardCharsets.UTF_8).trim();
                    }
                } catch (SocketTimeoutException ignored) {
                    // Продолжаем цикл до истечения общего таймаута
                }
            }
        } finally {
            sock.setSoTimeout(old);
        }
        return null;
    }
}