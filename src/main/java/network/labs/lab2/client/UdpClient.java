package network.labs.lab2.client;

import network.labs.lab2.common.Config;
import network.labs.lab2.common.NetworkUtils;
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
import java.util.Scanner;

/**
 * UDP-клиент для лабораторной работы №2.
 *
 * Реализует интерактивный интерфейс для отправки команд серверу (ECHO, TIME, CLOSE,
 * UPLOAD, DOWNLOAD) и управляет процессом передачи файлов.
 *
 * Особенности:
 * - Работает в одном потоке, использует блокирующий I/O с таймаутами.
 * - Фильтрует управляющие символы терминала и escape-последовательности.
 * - Поддерживает флаг --force для принудительной перезаписи файлов.
 * - Делегирует надёжную передачу данных классам ReliableSender/Receiver.
 * - Обрабатывает ошибки локально, не прерывая работу приложения.
 */
public final class UdpClient {

    /** Сокет для отправки/приёма UDP-датаграмм */
    private final DatagramSocket sock;

    /** Адрес сервера (хост:порт) */
    private final InetSocketAddress server;

    /** Буфер для приёма пакетов (1536 = 1465 payload + 71 запас на заголовки) */
    private final byte[] buf = new byte[1536];

    /**
     * Создаёт клиентское соединение.
     *
     * @param host IP-адрес или имя хоста сервера
     * @param port порт сервера
     * @throws IOException если не удалось создать сокет
     */
    public UdpClient(String host, int port) throws IOException {
        sock = new DatagramSocket();
        // Увеличиваем буферы для повышения пропускной способности в LAN
        sock.setReceiveBufferSize(Config.SOCK_BUF_SIZE);
        sock.setSendBufferSize(Config.SOCK_BUF_SIZE);
        // Таймаут для операций receive: 2 сек достаточно для команд, но не блокирует надолго
        sock.setSoTimeout(2000);
        server = new InetSocketAddress(host, port);
    }

    /**
     * Запускает интерактивный цикл обработки команд.
     * Читает ввод пользователя, фильтрует артефакты терминала,
     * передаёт команды на обработку и выводит результаты.
     *
     * @throws IOException при ошибке сетевого ввода-вывода
     */
    public void start() throws IOException {
        Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8);
        System.out.println("Команды: ECHO, TIME, CLOSE, UPLOAD <файл>, DOWNLOAD <файл>");

        while (true) {
            System.out.print("> ");
            String line = sc.nextLine().trim();

            // Пропускаем пустые строки и управляющие символы (кроме пробела)
            if (line.isEmpty() || line.codePoints().anyMatch(c -> c < 32 && c != ' ')) {
                continue;
            }
            // Игнорируем escape-последовательности терминала (стрелки, цвет и т.д.)
            if (line.startsWith("\u001b")) {
                continue;
            }

            // Если команда вернула true (CLOSE), завершаем цикл
            if (handle(line)) {
                break;
            }
        }
    }

    /**
     * Обрабатывает одну команду пользователя.
     *
     * Алгоритм:
     * 1. Очищает буфер сокета от старых пакетов.
     * 2. Извлекает флаг --force и удаляет его из команды перед отправкой.
     * 3. Отправляет "чистую" команду на сервер.
     * 4. Делегирует обработку UPLOAD/DOWNLOAD специализированным методам.
     * 5. Для простых команд (ECHO, TIME) ждёт и выводит ответ.
     *
     * @param cmd строка команды от пользователя
     * @return true, если нужно завершить работу клиента (команда CLOSE)
     * @throws IOException при ошибке сети
     */
    private boolean handle(String cmd) throws IOException {
        // Удаляем из сокета пакеты, оставшиеся от предыдущих операций
        drainBuffer();

        // Проверяем наличие флага принудительной перезаписи
        boolean force = cmd.contains("--force");
        // Убираем флаг из команды, чтобы сервер не пытался найти файл "--force"
        String cleanCmd = cmd.replaceAll("\\s*--force\\s*", " ").trim();

        if (!cleanCmd.isEmpty()) {
            send(cleanCmd);
        }

        String[] parts = cleanCmd.split("\\s+", 2);
        String action = parts[0].toUpperCase();
        String fname = parts.length > 1 ? parts[1] : "";

        // Делегируем обработку файловых операций
        if ("UPLOAD".equals(action) && !fname.isEmpty()) {
            return upload(fname, force);
        }
        if ("DOWNLOAD".equals(action) && !fname.isEmpty()) {
            return download(fname, force);
        }

        // Для простых команд ждём текстовый ответ и выводим его
        String resp = receiveCmd();
        if (resp.startsWith("ERROR:")) {
            System.out.println("Ошибка: " + resp);
        } else {
            System.out.println("Сервер: " + resp);
        }
        return "CLOSE".equals(action);
    }

    /**
     * Реализует логику команды UPLOAD.
     *
     * Этапы:
     * 1. Проверка существования и размера локального файла.
     * 2. Рукопожатие с сервером: получение "OK <offset>".
     * 3. Если файл уже загружен полностью:
     *    - с флагом --force: инициируем перезапись (отправляем offset=0),
     *    - без флага: завершаем с сообщением.
     * 4. Отправка размера оставшихся данных, ожидание "READY".
     * 5. Запуск надёжной передачи через ReliableSender.
     * 6. Вывод финального ответа сервера и расчёт битрейта.
     *
     * @param fname имя файла для отправки
     * @param force флаг принудительной перезаписи
     * @return false (команда не завершает сессию)
     */
    private boolean upload(String fname, boolean force) {
        Path src = Config.SRC_DIR.resolve(fname);
        try {
            // Проверка файла до начала сетевых операций
            if (!Files.exists(src)) {
                System.out.println("Файл не найден: " + src);
                return false;
            }
            long size = Files.size(src);
            if (size == 0) {
                System.out.println("Пустой файл: " + src);
                return false;
            }

            // Ждём ответ сервера на команду UPLOAD: "OK <offset>"
            String ok = receiveCmd();
            if (!ok.startsWith("OK ")) {
                System.out.println("Ошибка сервера: " + ok);
                return false;
            }
            long offset = Long.parseLong(ok.substring(3).trim());
            long rem = size - offset;

            // Обработка случая, когда файл уже загружен полностью
            if (force && rem <= 0) {
                System.out.println("Перезапись файла...");
                // Отправляем 0, чтобы сервер принял файл заново
                send("0");
                receiveCmd(); // READY
                rem = size;
                offset = 0;
            } else if (rem <= 0) {
                System.out.println("Файл уже загружен (используйте --force для перезаписи)");
                return false;
            } else {
                // Сообщаем серверу размер остатка и ждём готовности
                send(String.valueOf(rem));
                if (!"READY".equals(receiveCmd())) {
                    System.out.println("Сервер не готов к приёму");
                    return false;
                }
            }

            // Запуск передачи данных
            System.out.printf("Отправка: %s (%d байт%s)%n",
                    fname, rem, force ? ", перезапись" : "");
            long t0 = System.currentTimeMillis();

            try (var in = Files.newInputStream(src)) {
                in.skipNBytes(offset);
                new ReliableSender(sock, server).sendStream(in, rem);
            }

            try { Thread.sleep(100); } catch (InterruptedException ignored) {}

            String finalReply = "таймаут";
            for (int i = 0; i < 3; i++) {
                try {
                    sock.setSoTimeout(300);
                    finalReply = receiveCmd();
                    if (!"таймаут".equals(finalReply)) break;
                } catch (Exception ignored) {}
            }
            System.out.println("Сервер: " + finalReply);

            long elapsed = System.currentTimeMillis() - t0;
            System.out.printf("Скорость: %.1f Мбит/с%n", NetworkUtils.calcSpeedMbps(rem, elapsed));

        } catch (Exception e) {
            // Локальная обработка ошибок: вывод сообщения без прерывания программы
            System.out.println("Ошибка загрузки: " + e.getMessage());
        }
        return false;
    }

    /**
     * Реализует логику команды DOWNLOAD.
     *
     * Этапы:
     * 1. Получение подтверждения от сервера ("OK").
     * 2. Определение локального смещения (0 при --force или размер существующего файла).
     * 3. Отправка смещения серверу, получение размера остатка.
     * 4. Если остаток = 0: файл уже актуален (если не задан --force).
     * 5. Запуск надёжного приёма через ReliableReceiver.
     * 6. Вывод финального ответа и расчёт битрейта.
     *
     * @param fname имя файла для скачивания
     * @param force флаг принудительной перезаписи
     * @return false (команда не завершает сессию)
     */
    private boolean download(String fname, boolean force) {
        Path tgt = Config.DST_DIR.resolve("down_" + fname);
        try {
            String ok = receiveCmd();
            if (!"OK".equals(ok)) {
                System.out.println("Ошибка сервера: " + ok);
                return false;
            }

            // Определяем, с какого места продолжать (0 = начать заново)
            long loc = force || !Files.exists(tgt) ? 0 : Files.size(tgt);
            send(String.valueOf(loc));

            String sz = receiveCmd();
            if (sz == null) {
                System.out.println("Таймаут ответа сервера");
                return false;
            }
            long rem = Long.parseLong(sz.trim());

            if (rem <= 0) {
                System.out.println("Файл уже актуален (используйте --force для перезаписи)");
                return false;
            }

            // Запуск приёма данных
            System.out.printf("Приём: %s (%d байт%s)%n",
                    fname, rem, force ? ", перезапись" : "");
            long t0 = System.currentTimeMillis();

            try (var out = Files.newOutputStream(tgt, StandardOpenOption.CREATE,
                    loc > 0 ? StandardOpenOption.APPEND : StandardOpenOption.WRITE)) {
                new ReliableReceiver(sock, server).receiveStream(out, rem);
            }

            // Финальный ответ и статистика
            System.out.println("Сервер: " + receiveCmd());
            long elapsed = System.currentTimeMillis() - t0;
            System.out.printf("Скорость: %.1f Мбит/с%n", NetworkUtils.calcSpeedMbps(rem, elapsed));

        } catch (Exception e) {
            System.out.println("Ошибка скачивания: " + e.getMessage());
        }
        return false;
    }

    /**
     * Отправляет текстовую команду серверу в виде UDP-пакета.
     *
     * @param text текст команды
     * @throws IOException при ошибке отправки
     */
    private void send(String text) throws IOException {
        byte[] b = UdpPacket.cmd(text).toBytes();
        sock.send(new DatagramPacket(b, b.length, server));
    }

    /**
     * Ожидает и возвращает текстовый ответ от сервера.
     *
     * Особенности:
     * - Ждёт только пакеты с флагом CMD (игнорирует DATA/ACK/FIN).
     * - Использует общий таймаут 2 сек с внутренним интервалом 200 мс.
     * - Возвращает "таймаут" при истечении времени ожидания.
     *
     * @return текст ответа или "таймаут"
     * @throws IOException при ошибке сети
     */
    private String receiveCmd() throws IOException {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            try {
                sock.setSoTimeout(200);
                DatagramPacket dp = new DatagramPacket(buf, buf.length);
                sock.receive(dp);
                UdpPacket p = UdpPacket.fromBytes(dp.getData(), dp.getLength());
                // Принимаем только командные пакеты
                if (p.isCmd()) {
                    return new String(p.dataUnsafe(), 0, p.dataLength(), StandardCharsets.UTF_8).trim();
                }
            } catch (SocketTimeoutException ignored) {
                // Продолжаем цикл до истечения общего таймаута
            }
        }
        return "таймаут";
    }

    /**
     * Очищает входной буфер сокета от потерянных или устаревших пакетов.
     *
     * Используется перед началом новой команды, чтобы избежать
     * обработки ответов от предыдущих операций.
     *
     * @throws IOException при ошибке чтения сокета
     */
    private void drainBuffer() throws IOException {
        int old = sock.getSoTimeout();
        sock.setSoTimeout(50); // Короткий таймаут для быстрой очистки
        try {
            while (true) {
                try {
                    sock.receive(new DatagramPacket(new byte[1536], 1536));
                } catch (SocketTimeoutException e) {
                    break; // Буфер очищен
                }
            }
        } finally {
            sock.setSoTimeout(old);
        }
    }
}