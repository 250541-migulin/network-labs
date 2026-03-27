package network.labs.lab2.client;

import network.labs.lab2.common.Config;
import network.labs.lab2.common.IoUtils;
import network.labs.lab2.common.UdpPacket;
import network.labs.lab2.transport.ReliableUdpSender;
import network.labs.lab2.transport.ReliableUdpReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Scanner;

/**
 * Клиент для передачи файлов по UDP с поддержкой надёжности.
 *
 * Реализует:
 * - Команды: UPLOAD, DOWNLOAD, CLOSE, ECHO, TIME
 * - Надёжность: ACK, retransmit, sliding window
 * - Докачку файлов после обрыва
 * - Прогресс-бары и битрейт
 */
public class UdpClient {

    private static final Logger log = LoggerFactory.getLogger(UdpClient.class);

    private final String host;
    private final int port;

    /**
     * Создаёт клиента.
     *
     * @param host адрес сервера
     * @param port порт сервера
     */
    public UdpClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Запускает клиент: создаёт сокет и входит в цикл обработки команд.
     */
    public void start() throws IOException {
        log.debug("start: вход в метод");

        try (DatagramSocket socket = new DatagramSocket()) {

            // Настройка размеров буферов для высокой пропускной способности
            socket.setReceiveBufferSize(Config.SOCKET_BUFFER_SIZE);
            socket.setSendBufferSize(Config.SOCKET_BUFFER_SIZE);

            // Адрес сервера
            InetSocketAddress serverAddr = new InetSocketAddress(host, port);
            log.info("Подключено к {}:{}", host, port);

            // Цикл ввода команд
            Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
            log.debug("start: вход в цикл ввода команд");

            while (true) {
                System.out.print("> ");
                String line = scanner.nextLine().trim();

                if (line.isEmpty()) {
                    continue;
                }

                log.debug("start: пользователь ввёл '{}'", line);

                // Обработка команды
                String cmd = line.split("\\s+")[0].toUpperCase();
                log.debug("start: распознана команда '{}'", cmd);

                switch (cmd) {
                    case "ECHO" -> handleEcho(line, socket, serverAddr);
                    case "TIME" -> handleTime(socket, serverAddr);
                    case "CLOSE" -> { handleClose(socket, serverAddr); return; }
                    case "UPLOAD" -> handleUpload(line, socket, serverAddr);
                    case "DOWNLOAD" -> handleDownload(line, socket, serverAddr);
                    default -> handleUnknown(line, socket, serverAddr);
                }
            }

        } catch (SocketException e) {
            log.info("Сокет закрыт: {}", e.getMessage());
            System.out.println("Соединение закрыто: " + e.getMessage());

        } finally {
            log.debug("start: выход из метода");
        }
    }

    // ========================================================================
    // Простые команды
    // ========================================================================

    /**
     * Обработка команды ECHO.
     */
    private void handleEcho(String line, DatagramSocket socket, InetSocketAddress server) throws IOException {
        log.debug("handleEcho: вход, строка='{}'", line);

        String msg = line.length() > 5 ? line.substring(5).trim() : "";
        log.debug("handleEcho: сообщение='{}'", msg);

        // Отправляем команду
        UdpPacket cmdPacket = UdpPacket.createCommand(0, "ECHO " + msg);
        socket.send(new DatagramPacket(cmdPacket.toBytes(), cmdPacket.toBytes().length, server));
        log.debug("handleEcho: отправлена команда");

        // Ждём ответ
        DatagramPacket response = receivePacket(socket);
        if (response != null) {
            UdpPacket udpPacket = UdpPacket.fromBytes(response.getData(), response.getLength());
            if (udpPacket.isCommand()) {
                String reply = udpPacket.getCommand();
                log.debug("handleEcho: получен ответ='{}'", reply);
                System.out.println("Сервер: " + reply);
            }
        }

        log.debug("handleEcho: завершение");
    }

    /**
     * Обработка команды TIME.
     */
    private void handleTime(DatagramSocket socket, InetSocketAddress server) throws IOException {
        log.debug("handleTime: вход");

        // Отправляем команду через UdpPacket
        UdpPacket cmdPacket = UdpPacket.createCommand(0, "TIME");
        socket.send(new DatagramPacket(cmdPacket.toBytes(), cmdPacket.toBytes().length, server));
        log.debug("handleTime: отправлена команда");

        // Получаем ответ через UdpPacket
        DatagramPacket response = receivePacket(socket);
        if (response != null) {
            UdpPacket udpPacket = UdpPacket.fromBytes(response.getData(), response.getLength());
            if (udpPacket.isCommand()) {
                String time = udpPacket.getCommand();
                System.out.println("Сервер: " + time);
            }
        }
        log.debug("handleTime: завершение");
    }

    /**
     * Обработка команды CLOSE.
     */
    private void handleClose(DatagramSocket socket, InetSocketAddress server) throws IOException {
        log.debug("handleClose: вход");

        // Отправляем команду
        UdpPacket cmdPacket = UdpPacket.createCommand(0, "CLOSE");
        socket.send(new DatagramPacket(cmdPacket.toBytes(), cmdPacket.toBytes().length, server));
        log.debug("handleClose: отправлена команда");

        // Ждём подтверждение (опционально)
        DatagramPacket response = receivePacket(socket);
        if (response != null) {
            UdpPacket udpPacket = UdpPacket.fromBytes(response.getData(), response.getLength());
            if (udpPacket.isCommand()) {
                String reply = udpPacket.getCommand();
                log.debug("handleClose: получен ответ='{}'", reply);
                System.out.println("Сервер: " + reply);
            }
        }

        log.debug("handleClose: завершение");
    }

    /**
     * Обработка неизвестной команды.
     */
    private void handleUnknown(String line, DatagramSocket socket, InetSocketAddress server) throws IOException {
        log.debug("handleUnknown: вход, команда='{}'", line);

        UdpPacket cmdPacket = UdpPacket.createCommand(0, line);
        socket.send(new DatagramPacket(cmdPacket.toBytes(), cmdPacket.toBytes().length, server));
        log.debug("handleUnknown: отправлена команда");

        // Ждём ответ (ошибка)
        DatagramPacket response = receivePacket(socket);
        if (response != null) {
            UdpPacket udpPacket = UdpPacket.fromBytes(response.getData(), response.getLength());
            if (udpPacket.isCommand()) {
                String error = udpPacket.getCommand();
                log.debug("handleUnknown: получен ответ='{}'", error);
                System.out.println("Сервер: " + error);
            }
        }

        log.debug("handleUnknown: завершение");
    }

    // ========================================================================
    // Сложные команды: UPLOAD
    // ========================================================================

    /**
     * Обработка команды UPLOAD: клиент отправляет файл на сервер.
     */
    private void handleUpload(String line, DatagramSocket socket, InetSocketAddress server) throws IOException {
        log.debug("handleUpload: старт, команда='{}'", line);

        // 0. Парсинг аргументов
        String[] parts = line.split("\\s+");
        if (parts.length < 2) {
            System.out.println("Использование: UPLOAD <имя файла>");
            log.debug("handleUpload: ошибка — не указано имя файла, возврат");
            return;
        }

        String filename = parts[1];
        Path file = Config.SOURCE_DIR.resolve(filename);

        // 1. Проверяем файл локально
        log.debug("handleUpload: шаг 1 — проверка файла '{}', существует={}", file, Files.exists(file));
        if (!Files.exists(file)) {
            System.out.println("Файл не найден: " + file);
            log.debug("handleUpload: файл не найден, возврат без отправки команды");
            return;
        }

        long fileSize = Files.size(file);
        log.debug("handleUpload: шаг 1 — размер файла={}", fileSize);

        // 2. Отправляем команду серверу
        log.debug("handleUpload: шаг 2 — отправка команды 'UPLOAD {}'", filename);
        UdpPacket cmdPacket = UdpPacket.createCommand(0, "UPLOAD " + filename);
        socket.send(new DatagramPacket(cmdPacket.toBytes(), cmdPacket.toBytes().length, server));

        // 3. Ждём ответ от сервера: "OK <offset>"
        log.debug("handleUpload: шаг 3 — чтение ответа сервера...");
        DatagramPacket response = receivePacket(socket);
        if (response == null) {
            System.out.println("Сервер закрыл соединение");
            log.debug("handleUpload: ответ null, соединение закрыто, возврат");
            return;
        }

        UdpPacket udpPacket = UdpPacket.fromBytes(response.getData(), response.getLength());
        String okLine = udpPacket.getCommand();
        log.debug("handleUpload: шаг 3 — ответ получен: '{}'", okLine);

        if (okLine == null || !okLine.startsWith("OK ")) {
            System.out.println("Сервер: " + (okLine != null ? okLine : "нет ответа"));
            log.debug("handleUpload: ответ не начинается с 'OK', возврат");
            return;
        }

        // 4. Парсим offset для докачки
        long offset = 0;
        try {
            offset = Long.parseLong(okLine.substring(3).trim());
        } catch (NumberFormatException e) {
            System.out.println("Сервер: некорректный offset");
            log.debug("handleUpload: ошибка парсинга offset", e);
            return;
        }
        log.debug("handleUpload: шаг 4 — offset={}", offset);

        long remaining = fileSize - offset;
        log.debug("handleUpload: шаг 4 — размер файла={}, осталось отправить={}", fileSize, remaining);

        // 5. Если файл уже полностью загружен — завершаем
        if (remaining <= 0) {
            System.out.println("Файл уже полностью загружен на сервере");
            log.debug("handleUpload: шаг 5 — remaining<=0, файл загружен полностью, возврат");
            return;
        }

        // 6. Отправляем размер остатка
        log.debug("handleUpload: шаг 6 — отправка размера остатка: {}", remaining);
        UdpPacket sizePacket = UdpPacket.createCommand(0, String.valueOf(remaining));
        socket.send(new DatagramPacket(sizePacket.toBytes(), sizePacket.toBytes().length, server));

        // Ждём READY
        DatagramPacket ready = receivePacket(socket);
        if (ready == null) {
            System.out.println("Сервер: нет ответа");
            log.debug("handleUpload: не получен READY, возврат");
            return;
        }

        UdpPacket readyPacket = UdpPacket.fromBytes(ready.getData(), ready.getLength());
        if (!"READY".equals(readyPacket.getCommand())) {
            System.out.println("Сервер: " + readyPacket.getCommand());
            log.debug("handleUpload: не получен READY, возврат");
            return;
        }
        log.debug("handleUpload: шаг 6 — получен READY");

        log.info("Отправка: {} ({} байт, докачка с {})", filename, remaining, offset);

        // 7. Отправляем файл через ReliableUdpSender
        log.debug("handleUpload: шаг 7 — вызов ReliableUdpSender (файл -> сеть)");
        long startTime = System.currentTimeMillis();

        try (var fis = Files.newInputStream(file)) {
            fis.skipNBytes(offset);
            new ReliableUdpSender(socket, server).sendStream(fis, remaining);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Отправлено {} байт за {} мс", remaining, elapsed);
        log.info("Битрейт: {}", IoUtils.formatBitrate(remaining, elapsed));

        // 8. Ждём финальное подтверждение
        log.debug("handleUpload: шаг 8 — чтение финального подтверждения...");
        DatagramPacket finalResp = receivePacket(socket);
        if (finalResp != null) {
            UdpPacket finalPacket = UdpPacket.fromBytes(finalResp.getData(), finalResp.getLength());
            if (finalPacket.isCommand()) {
                String finalMsg = finalPacket.getCommand();
                log.debug("handleUpload: шаг 8 — финальный ответ: '{}'", finalMsg);
                System.out.println("Сервер: " + finalMsg);
            }
        }

        log.debug("handleUpload: завершение");
    }

    // ========================================================================
    // Сложные команды: DOWNLOAD
    // ========================================================================

    /**
     * Обработка команды DOWNLOAD: клиент получает файл с сервера.
     */
    private void handleDownload(String line, DatagramSocket socket, InetSocketAddress server) throws IOException {
        log.debug("handleDownload: старт, команда='{}'", line);

        // 0. Парсинг аргументов
        String[] parts = line.split("\\s+");
        if (parts.length < 2) {
            System.out.println("Использование: DOWNLOAD <имя файла>");
            log.debug("handleDownload: ошибка — не указано имя файла, возврат");
            return;
        }

        String filename = parts[1];
        Path target = Config.TMP_DIR.resolve("downloaded_" + filename);

        // 1. Проверяем локальный файл для докачки
        long localSize = Files.exists(target) ? Files.size(target) : 0;
        log.debug("handleDownload: шаг 1 — локальный файл '{}', размер={}", target, localSize);

        if (localSize > 0) {
            log.info("Найден локальный файл ({} байт). Проверяем докачку...", localSize);
            log.debug("handleDownload: файл найден, возможна докачка с байта {}", localSize);
        }

        // 2. Отправляем команду серверу
        String request = "DOWNLOAD " + filename + (localSize > 0 ? " " + localSize : "");
        log.debug("handleDownload: шаг 2 — отправка команды '{}'", request);
        UdpPacket cmdPacket = UdpPacket.createCommand(0, request);
        socket.send(new DatagramPacket(cmdPacket.toBytes(), cmdPacket.toBytes().length, server));

        // 3. Ждём статус от сервера
        log.debug("handleDownload: шаг 3 — чтение статуса от сервера...");
        DatagramPacket statusPacket = receivePacket(socket);
        if (statusPacket == null) {
            System.out.println("Сервер закрыл соединение");
            log.debug("handleDownload: статус null, соединение закрыто, возврат");
            return;
        }

        UdpPacket statusUdp = UdpPacket.fromBytes(statusPacket.getData(), statusPacket.getLength());
        String status = statusUdp.getCommand();
        log.debug("handleDownload: шаг 3 — статус получен: '{}'", status);

        if (status == null || !status.startsWith("OK ")) {
            System.out.println("Сервер: " + (status != null ? status : "нет ответа"));
            log.debug("handleDownload: статус не 'OK', возврат");
            return;
        }

        // 4. Парсим размер файла
        long fileSize = 0;
        try {
            fileSize = Long.parseLong(status.substring(3).trim());
        } catch (NumberFormatException e) {
            System.out.println("Сервер: некорректный размер файла");
            log.debug("handleDownload: ошибка парсинга fileSize", e);
            return;
        }
        log.debug("handleDownload: шаг 4 — размер файла={}", fileSize);

        long remaining = fileSize - localSize;
        log.debug("handleDownload: шаг 4 — осталось принять={}", remaining);

        // Ждём READY
        DatagramPacket ready = receivePacket(socket);
        if (ready == null) {
            System.out.println("Сервер: нет ответа");
            log.debug("handleDownload: не получен READY, возврат");
            return;
        }

        UdpPacket readyPacket = UdpPacket.fromBytes(ready.getData(), ready.getLength());
        if (!"READY".equals(readyPacket.getCommand())) {
            System.out.println("Сервер: " + readyPacket.getCommand());
            log.debug("handleDownload: не получен READY, возврат");
            return;
        }
        log.debug("handleDownload: шаг 4 — получен READY");

        // 5. Если файл уже актуален — завершаем
        if (remaining <= 0) {
            System.out.println("Файл уже актуален");
            log.debug("handleDownload: шаг 5 — remaining<=0, файл актуален, возврат");
            return;
        }

        log.info("Приём: {} ({} байт, всего: {} байт)", filename, remaining, fileSize);
        log.debug("handleDownload: шаг 5 — начинаю приём данных, ожидаю {} байт", remaining);

        // 6. Принимаем данные через ReliableUdpReceiver
        log.debug("handleDownload: шаг 6 — вызов ReliableUdpReceiver (сеть -> файл)");
        long startTime = System.currentTimeMillis();

        try (var fos = Files.newOutputStream(target, StandardOpenOption.CREATE,
                localSize > 0 ? StandardOpenOption.APPEND : StandardOpenOption.WRITE)) {
            new ReliableUdpReceiver(socket, server, fos).receiveStream(remaining);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Получено {} байт за {} мс", remaining, elapsed);
        log.info("Битрейт: {}", IoUtils.formatBitrate(remaining, elapsed));

        // 7. Ждём финальное подтверждение
        log.debug("handleDownload: шаг 7 — чтение финального подтверждения...");
        DatagramPacket finalResp = receivePacket(socket);
        if (finalResp != null) {
            UdpPacket finalPacket = UdpPacket.fromBytes(finalResp.getData(), finalResp.getLength());
            if (finalPacket.isCommand()) {
                String finalMsg = finalPacket.getCommand();
                log.debug("handleDownload: шаг 7 — финальный ответ: '{}'", finalMsg);
                System.out.println("Сервер: " + finalMsg);
            }
        }

        System.out.println("Файл сохранён: " + target.getFileName());
        log.debug("handleDownload: завершение, файл сохранён в '{}'", target);
    }

    // ========================================================================
    // Утилиты
    // ========================================================================

    /**
     * Получает UDP-пакет с обработкой таймаутов.
     */
    private DatagramPacket receivePacket(DatagramSocket socket) throws IOException {
        log.debug("receivePacket: ожидание пакета...");

        byte[] buf = new byte[Config.UDP_MAX_PAYLOAD + 8];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);

        try {
            socket.receive(packet);
            log.debug("receivePacket: получен пакет ({} байт)", packet.getLength());
            return packet;
        } catch (SocketTimeoutException e) {
            log.debug("receivePacket: таймаут ожидания пакета");
            return null;
        }
    }
}