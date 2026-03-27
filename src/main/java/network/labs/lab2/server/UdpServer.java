package network.labs.lab2.server;

import network.labs.lab2.common.Config;
import network.labs.lab2.common.IoUtils;
import network.labs.lab2.common.UdpPacket;
import network.labs.lab2.transport.ReliableUdpReceiver;
import network.labs.lab2.transport.ReliableUdpSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Сервер для передачи файлов по UDP с поддержкой надёжности.
 *
 * Реализует:
 * - Команды: UPLOAD, DOWNLOAD, CLOSE, ECHO, TIME
 * - Надёжность: ACK, retransmit, sliding window
 * - Докачку файлов после обрыва
 * - Детект обрыва сессии (таймаут неактивности)
 */
public class UdpServer {

    private static final Logger log = LoggerFactory.getLogger(UdpServer.class);

    private final int port;
    private static final int HEADER_SIZE = 7; // 2+2+1+2 байта

    // Состояние для докачки (запоминаем последнего клиента и файл)
    private InetSocketAddress lastClientAddr = null;
    private String lastFilename = null;

    /**
     * Создаёт сервер.
     *
     * @param port порт для прослушивания
     */
    public UdpServer(int port) {
        this.port = port;
    }

    /**
     * Запускает сервер: создаёт сокет и входит в цикл приёма пакетов.
     * Метод блокирующий — не вернёт управление, пока сервер работает.
     */
    public void start() {
        log.info("Сервер запущен на порту {}", port);

        try (DatagramSocket socket = new DatagramSocket(port)) {

            // Настройка размеров буферов для высокой пропускной способности
            socket.setReceiveBufferSize(Config.SOCKET_BUFFER_SIZE);
            socket.setSendBufferSize(Config.SOCKET_BUFFER_SIZE);

            byte[] buffer = new byte[Config.UDP_MAX_PAYLOAD + HEADER_SIZE];

            // Бесконечный цикл: ждём пакет → обрабатываем → повторяем
            while (true) {
                // Приём пакета (блокируется, пока не придёт)
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);  // ← Просто ждём.

                InetSocketAddress clientAddr = new InetSocketAddress(
                        packet.getAddress(), packet.getPort()
                );

                log.debug("Получен пакет от {} ({} байт)", clientAddr, packet.getLength());

                //  Обработка
                handlePacket(socket, clientAddr, packet);
            }

        } catch (IOException e) {
            log.error("Сервер остановлен: {}", e.getMessage());
        }
    }

    // ========================================================================
    // Обработка пакетов
    // ========================================================================

    /**
     * Обрабатывает полученный UDP-пакет.
     * Распознаёт тип пакета (команда, данные, ACK) и делегирует обработку.
     */
    private void handlePacket(DatagramSocket socket, InetSocketAddress clientAddr,
                              DatagramPacket packet) throws IOException {

        UdpPacket udpPacket;
        try {
            udpPacket = UdpPacket.fromBytes(packet.getData(), packet.getLength());
        } catch (IOException e) {
            log.warn("Некорректный пакет от {}: {}", clientAddr, e.getMessage());
            return;
        }

        log.debug("handlePacket: получен {}", udpPacket);

        // Маршрутизация по типу пакета
        if (udpPacket.isCommand()) {
            handleCommand(socket, clientAddr, udpPacket);
        } else if (udpPacket.isData()) {
            // Данные файла обрабатываются в ReliableUdpReceiver
            log.debug("handlePacket: пакет данных (обрабатывается в ReliableUdpReceiver)");
        } else if (udpPacket.isAck()) {
            // ACK обрабатываются в ReliableUdpSender
            log.debug("handlePacket: подтверждение (обрабатывается в ReliableUdpSender)");
        } else {
            log.warn("Неизвестный тип пакета от {}", clientAddr);
        }
    }

    // ========================================================================
    // Обработка команд
    // ========================================================================

    /**
     * Обрабатывает текстовую команду от клиента.
     */
    private void handleCommand(DatagramSocket socket, InetSocketAddress clientAddr,
                               UdpPacket packet) throws IOException {

        String command = packet.getCommand();
        if (command == null) {
            log.warn("Пустая команда от {}", clientAddr);
            return;
        }

        log.debug("handleCommand: команда='{}'", command);

        String[] parts = command.split("\\s+");
        String cmdName = parts[0].toUpperCase();

        switch (cmdName) {
            case "ECHO" -> handleEcho(socket, clientAddr, command);
            case "TIME" -> handleTime(socket, clientAddr);
            case "CLOSE" -> handleClose(socket, clientAddr);
            case "UPLOAD" -> handleUpload(socket, clientAddr, command, parts);
            case "DOWNLOAD" -> handleDownload(socket, clientAddr, command, parts);
            default -> handleUnknown(socket, clientAddr, command);
        }
    }

    // ========================================================================
    // Простые команды
    // ========================================================================

    /**
     * Обработка команды ECHO.
     */
    private void handleEcho(DatagramSocket socket, InetSocketAddress clientAddr, String command) throws IOException {
        log.debug("handleEcho: вход, команда='{}'", command);

        String msg = command.length() > 5 ? command.substring(5).trim() : "";
        String reply = "Эхо: " + msg;

        // Отправляем ответ через UdpPacket
        UdpPacket response = UdpPacket.createCommand(0, reply);
        socket.send(new DatagramPacket(response.toBytes(), response.toBytes().length,
                clientAddr.getAddress(), clientAddr.getPort()));

        log.debug("handleEcho: отправлен ответ='{}'", reply);
    }

    /**
     * Обработка команды TIME.
     */
    private void handleTime(DatagramSocket socket, InetSocketAddress clientAddr) throws IOException {
        log.debug("handleTime: вход");

        String time = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String reply = "Текущее время: " + time;

        // Отправляем ответ через UdpPacket
        UdpPacket response = UdpPacket.createCommand(0, reply);
        socket.send(new DatagramPacket(response.toBytes(), response.toBytes().length,
                clientAddr.getAddress(), clientAddr.getPort()));

        log.debug("handleTime: отправлен ответ='{}'", reply);
    }

    /**
     * Обработка команды CLOSE.
     */
    private void handleClose(DatagramSocket socket, InetSocketAddress clientAddr) throws IOException {
        log.debug("handleClose: вход");

        UdpPacket response = UdpPacket.createCommand(0, "Соединение закрыто");
        socket.send(new DatagramPacket(response.toBytes(), response.toBytes().length,
                clientAddr.getAddress(), clientAddr.getPort()));

        log.debug("handleClose: отправлен ответ");

        // Сбрасываем сессию
        lastClientAddr = null;
        lastFilename = null;
    }

    /**
     * Обработка неизвестной команды.
     * Показывает пользователю, что именно он ввёл, и список доступных команд.
     */
    private void handleUnknown(DatagramSocket socket, InetSocketAddress clientAddr,
                               String command) throws IOException {

        log.debug("handleUnknown: вход, команда='{}'", command);

        // Формируем понятное сообщение с тем, что ввёл пользователь
        String receivedCmd = command.split("\\s+")[0].toUpperCase();
        String errorMsg = String.format("ERROR неизвестная команда '%s'. Доступные: ECHO, TIME, UPLOAD, DOWNLOAD, CLOSE",
                receivedCmd);

        UdpPacket response = UdpPacket.createCommand(0, errorMsg);
        socket.send(new DatagramPacket(response.toBytes(), response.toBytes().length,
                clientAddr.getAddress(), clientAddr.getPort()));

        log.debug("handleUnknown: отправлена ошибка: '{}'", errorMsg);
    }

    // ========================================================================
    // Сложные команды: UPLOAD
    // ========================================================================

    /**
     * Обработка команды UPLOAD: сервер принимает файл от клиента.
     *
     * Протокол:
     * 1. Сервер получает команду "UPLOAD <filename>"
     * 2. Сервер проверяет докачку (если тот же клиент и файл)
     * 3. Сервер → Клиент: "OK <offset>"
     * 4. Клиент → Сервер: <remaining>
     * 5. Сервер → Клиент: "READY"
     * 6. Клиент → Сервер: [данные файла]
     * 7. Сервер → Клиент: "Файл загружен"
     */
    private void handleUpload(DatagramSocket socket, InetSocketAddress clientAddr,
                              String command, String[] parts) throws IOException {

        log.debug("handleUpload: старт, команда='{}', клиент={}", command, clientAddr);

        // 0. Проверка аргументов
        if (parts.length < 2) {
            UdpPacket error = UdpPacket.createCommand(0, "ERROR имя файла не указано");
            socket.send(new DatagramPacket(error.toBytes(), error.toBytes().length,
                    clientAddr.getAddress(), clientAddr.getPort()));
            log.debug("handleUpload: ошибка — не указано имя файла");
            return;
        }

        String filename = parts[1];
        Path target = Config.TMP_DIR.resolve(filename);

        // 1. Проверяем докачку: если тот же клиент и файл — продолжаем с места обрыва
        log.debug("handleUpload: шаг 1 — проверка докачки: файл='{}', существует={}",
                filename, Files.exists(target));

        long offset = 0;
        boolean isNewFile = !Files.exists(target);

        if (!isNewFile) {
            if (isSameClientAndFile(clientAddr, filename)) {
                offset = Files.size(target);
                log.info("Докачка: {} байт уже есть", offset);
                log.debug("handleUpload: шаг 1 — докачка: тот же клиент и файл, offset={}", offset);
            } else {
                Files.delete(target);
                isNewFile = true;
                log.debug("handleUpload: шаг 1 — файл существует, но клиент другой, удаляю старый");
            }
        } else {
            log.debug("handleUpload: шаг 1 — файл не существует, начинаем с нуля");
        }

        // 2. Отправляем OK с offset (клиент ждёт это перед отправкой размера!)
        log.debug("handleUpload: шаг 2 — отправка 'OK {}' клиенту", offset);
        UdpPacket okPacket = UdpPacket.createCommand(0, "OK " + offset);
        socket.send(new DatagramPacket(okPacket.toBytes(), okPacket.toBytes().length,
                clientAddr.getAddress(), clientAddr.getPort()));

        // 3. Читаем размер остатка от клиента
        log.debug("handleUpload: шаг 3 — чтение размера остатка от клиента...");
        DatagramPacket sizePacket = receivePacket(socket, clientAddr);
        if (sizePacket == null) {
            log.debug("handleUpload: шаг 3 — таймаут ожидания размера");
            return;
        }

        // Конвертируем DatagramPacket → UdpPacket
        UdpPacket sizeUdp = UdpPacket.fromBytes(sizePacket.getData(), sizePacket.getLength());
        String sizeLine = sizeUdp.getCommand();
        log.debug("handleUpload: шаг 3 — размер получен: '{}'", sizeLine);

        long remaining;
        try {
            remaining = Long.parseLong(sizeLine.trim());
        } catch (NumberFormatException e) {
            UdpPacket error = UdpPacket.createCommand(0, "ERROR неверный размер");
            socket.send(new DatagramPacket(error.toBytes(), error.toBytes().length,
                    clientAddr.getAddress(), clientAddr.getPort()));
            log.error("handleUpload: неверный формат размера '{}'", sizeLine);
            return;
        }

        log.debug("handleUpload: шаг 3 — remaining={}", remaining);

        // 4. Отправляем READY
        log.debug("handleUpload: шаг 4 — отправка 'READY'");
        UdpPacket readyPacket = UdpPacket.createCommand(0, "READY");
        socket.send(new DatagramPacket(readyPacket.toBytes(), readyPacket.toBytes().length,
                clientAddr.getAddress(), clientAddr.getPort()));

        // 5. Проверка на полный файл
        if (remaining <= 0) {
            log.debug("handleUpload: шаг 5 — remaining<=0, файл уже загружен");
            UdpPacket donePacket = UdpPacket.createCommand(0, "Файл уже загружен");
            socket.send(new DatagramPacket(donePacket.toBytes(), donePacket.toBytes().length,
                    clientAddr.getAddress(), clientAddr.getPort()));
            return;
        }

        log.info("Приём файла: {} ({} байт)", filename, remaining);
        log.debug("handleUpload: шаг 5 — начинаю приём данных, ожидаю {} байт", remaining);

        // 6. Приём данных через ReliableUdpReceiver (с прогрессом)
        log.debug("handleUpload: шаг 6 — вызов ReliableUdpReceiver (сеть -> файл)");
        long startTime = System.currentTimeMillis();

        try (var fos = Files.newOutputStream(target,
                java.nio.file.StandardOpenOption.CREATE,
                offset > 0 ? java.nio.file.StandardOpenOption.APPEND
                        : java.nio.file.StandardOpenOption.WRITE)) {

            new ReliableUdpReceiver(socket, clientAddr, fos).receiveStream(remaining);

        } catch (IOException e) {
            log.warn("UPLOAD прерван: {}", e.getMessage());
            setLastSession(clientAddr, filename);
            return;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        long fileSize = Files.size(target);

        log.info("Получено {} байт за {} мс", fileSize, elapsed);
        log.info("Битрейт: {}", IoUtils.formatBitrate(fileSize, elapsed));

        // 7. Финальное подтверждение + сохранение состояния для докачки
        log.debug("handleUpload: шаг 7 — отправка финального подтверждения");
        setLastSession(clientAddr, filename);

        UdpPacket donePacket = UdpPacket.createCommand(0,
                "Файл загружен: " + filename);
        socket.send(new DatagramPacket(donePacket.toBytes(), donePacket.toBytes().length,
                clientAddr.getAddress(), clientAddr.getPort()));

        log.debug("handleUpload: завершение");
    }

    // ========================================================================
    // Сложные команды: DOWNLOAD
    // ========================================================================

    /**
     * Обработка команды DOWNLOAD: сервер отправляет файл клиенту.
     *
     * Протокол:
     * 1. Сервер получает команду "DOWNLOAD <filename> [offset]"
     * 2. Сервер проверяет существование файла
     * 3. Сервер → Клиент: "OK <fileSize>"
     * 4. Сервер → Клиент: "READY"
     * 5. Если remaining > 0: Сервер → Клиент: [данные файла]
     * 6. Сервер → Клиент: "Файл отправлен"
     */
    private void handleDownload(DatagramSocket socket, InetSocketAddress clientAddr,
                                String command, String[] parts) throws IOException {

        log.debug("handleDownload: старт, команда='{}', клиент={}", command, clientAddr);

        // 0. Проверка аргументов
        if (parts.length < 2) {
            UdpPacket error = UdpPacket.createCommand(0, "ERROR имя файла не указано");
            socket.send(new DatagramPacket(error.toBytes(), error.toBytes().length,
                    clientAddr.getAddress(), clientAddr.getPort()));
            log.debug("handleDownload: ошибка — не указано имя файла");
            return;
        }

        String filename = parts[1];
        Path source = Config.SOURCE_DIR.resolve(filename);

        // 1. Проверяем, что файл существует на сервере
        log.debug("handleDownload: шаг 1 — проверка файла: путь='{}', существует={}",
                source, Files.exists(source));

        if (!Files.exists(source)) {
            UdpPacket error = UdpPacket.createCommand(0, "ERROR файл не найден");
            socket.send(new DatagramPacket(error.toBytes(), error.toBytes().length,
                    clientAddr.getAddress(), clientAddr.getPort()));
            log.debug("handleDownload: шаг 1 — файл не найден, отправляю ошибку");
            return;
        }
        log.debug("handleDownload: шаг 1 — файл найден, размер={}", Files.size(source));

        // 2. Читаем offset от клиента (для докачки)
        long requestedOffset = 0;
        if (parts.length >= 3) {
            try {
                requestedOffset = Long.parseLong(parts[2]);
            } catch (NumberFormatException e) {
                UdpPacket error = UdpPacket.createCommand(0, "ERROR некорректный offset");
                socket.send(new DatagramPacket(error.toBytes(), error.toBytes().length,
                        clientAddr.getAddress(), clientAddr.getPort()));
                log.debug("handleDownload: некорректный offset");
                return;
            }
        }

        long fileSize = Files.size(source);
        long offset = Math.min(requestedOffset, fileSize);
        long remaining = fileSize - offset;
        log.debug("handleDownload: шаг 2 — fileSize={}, offset={}, remaining={}",
                fileSize, offset, remaining);

        // 3. Отправляем OK с размером файла
        log.debug("handleDownload: шаг 3 — отправка 'OK {}' клиенту", fileSize);
        UdpPacket okPacket = UdpPacket.createCommand(0, "OK " + fileSize);
        socket.send(new DatagramPacket(okPacket.toBytes(), okPacket.toBytes().length,
                clientAddr.getAddress(), clientAddr.getPort()));

        // 4. Отправляем READY
        log.debug("handleDownload: шаг 4 — отправка 'READY'");
        UdpPacket readyPacket = UdpPacket.createCommand(0, "READY");
        socket.send(new DatagramPacket(readyPacket.toBytes(), readyPacket.toBytes().length,
                clientAddr.getAddress(), clientAddr.getPort()));

        // 5. Если файл уже актуален — завершаем
        if (remaining <= 0) {
            log.debug("handleDownload: шаг 5 — remaining<=0, файл уже актуален");
            UdpPacket donePacket = UdpPacket.createCommand(0, "Файл уже актуален");
            socket.send(new DatagramPacket(donePacket.toBytes(), donePacket.toBytes().length,
                    clientAddr.getAddress(), clientAddr.getPort()));
            return;
        }

        log.info("Отправка файла: {} ({} байт)", filename, remaining);
        log.debug("handleDownload: шаг 5 — начинаю отправку данных, пропуск первых {} байт", offset);

        // 6. Отправка данных через ReliableUdpSender (с прогрессом)
        log.debug("handleDownload: шаг 6 — вызов ReliableUdpSender (файл -> сеть)");
        long startTime = System.currentTimeMillis();

        try (var fis = Files.newInputStream(source)) {
            fis.skipNBytes(offset);

            new ReliableUdpSender(socket, clientAddr).sendStream(fis, remaining);

        } catch (IOException e) {
            log.warn("DOWNLOAD прерван: {}", e.getMessage());
            return;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Отправлено {} байт за {} мс", remaining, elapsed);
        log.info("Битрейт: {}", IoUtils.formatBitrate(remaining, elapsed));

        // 7. Финальное подтверждение
        log.debug("handleDownload: шаг 7 — отправка финального подтверждения");
        UdpPacket donePacket = UdpPacket.createCommand(0,
                "Файл отправлен: " + filename);
        socket.send(new DatagramPacket(donePacket.toBytes(), donePacket.toBytes().length,
                clientAddr.getAddress(), clientAddr.getPort()));

        log.debug("handleDownload: завершение");
    }

    // ========================================================================
    // Утилиты
    // ========================================================================

    /**
     * Получает UDP-пакет от конкретного клиента с обработкой таймаутов.
     */
    private DatagramPacket receivePacket(DatagramSocket socket,
                                         InetSocketAddress expectedClient) throws IOException {
        log.debug("receivePacket: ожидание пакета от {}", expectedClient);

        byte[] buf = new byte[Config.UDP_MAX_PAYLOAD + 8];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);

        try {
            socket.receive(packet);

            InetSocketAddress sender = new InetSocketAddress(
                    packet.getAddress(), packet.getPort()
            );

            if (!sender.equals(expectedClient)) {
                log.debug("receivePacket: пакет от другого клиента {}, игнорирую", sender);
                return receivePacket(socket, expectedClient); // рекурсивно ждём нужного
            }

            log.debug("receivePacket: получен пакет от {} ({} байт)", sender, packet.getLength());
            return packet;

        } catch (SocketTimeoutException e) {
            log.debug("receivePacket: таймаут ожидания пакета");
            return null;
        }
    }

    /**
     * Проверяет, тот же ли клиент (по IP) и файл для докачки.
     */
    public boolean isSameClientAndFile(InetSocketAddress client, String filename) {
        return lastClientAddr != null
                && client.getAddress().equals(lastClientAddr.getAddress())  // ✅ Только IP!
                && lastFilename != null
                && lastFilename.equals(filename);
    }

    /**
     * Сохраняет состояние сессии для докачки.
     */
    public void setLastSession(InetSocketAddress client, String filename) {
        this.lastClientAddr = client;
        this.lastFilename = filename;
        log.debug("setLastSession: сохранено состояние — клиент={}, файл={}", client, filename);
    }
}