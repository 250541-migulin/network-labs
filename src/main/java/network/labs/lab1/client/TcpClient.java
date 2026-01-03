package network.labs.lab1.client;

import network.labs.lab1.common.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

/**
 * TCP-клиент для лабораторной работы №1.
 * Поддерживает команды: ECHO, TIME, CLOSE/QUIT/EXIT, UPLOAD, DOWNLOAD.
 */
public class TcpClient {
    private static final Logger log = LoggerFactory.getLogger(TcpClient.class);

    private final String host;
    private final int port;
    private final Path clientDir;

    /**
     * Конструктор клиента.
     *
     * @param host адрес сервера
     * @param port порт сервера
     */
    public TcpClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.clientDir = Path.of("client_files");
        initClientDirectory();
    }

    /**
     * Создаёт директорию для клиентских файлов.
     */
    private void initClientDirectory() {
        try {
            Files.createDirectories(clientDir);
            log.info("Директория клиента готова: {}", clientDir.toAbsolutePath());
        } catch (IOException e) {
            log.error("Не удалось создать директорию клиента", e);
            throw new IllegalStateException("Директория клиента недоступна", e);
        }
    }

    /**
     * Запускает клиент и обрабатывает команды пользователя.
     */
    public void start() {
        log.info("Подключение к {}:{}", host, port);
        try (Socket socket = new Socket(host, port)) {
            socket.setKeepAlive(true);
            log.info("Соединение установлено с {}:{}", host, port);

            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            Scanner scanner = new Scanner(System.in, "UTF-8");

            System.out.println("Подключено к " + host + ":" + port);
            System.out.println("Команды: ECHO <текст>, TIME, CLOSE, UPLOAD/DOWNLOAD <файл>");

            boolean running = true;
            while (running) {
                System.out.print("> ");
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("\\s+", 2);
                String command = parts[0].toUpperCase();

                try {
                    switch (command) {
                        case "UPLOAD" -> handleUpload(parts, in, out);
                        case "DOWNLOAD" -> handleDownload(parts, in, out);
                        case "CLOSE", "QUIT", "EXIT" -> {
                            IoUtils.writeLine(out, line);
                            String response = IoUtils.readLine(in);
                            System.out.println("Сервер: " + (response != null ? response : "Соединение закрыто"));
                            running = false;
                        }
                        default -> {
                            String response = sendCommandAndReadResponse(line, in, out);
                            if (response == null) {
                                System.out.println("Сервер закрыл соединение");
                                running = false;
                            } else {
                                System.out.println("Сервер: " + response);
                            }
                        }
                    }
                } catch (IOException e) {
                    log.error("Ошибка выполнения команды", e);
                    System.out.println("Ошибка: " + e.getMessage());
                    running = false;
                }
            }

        } catch (IOException e) {
            log.error("Не удалось подключиться к {}:{}", host, port, e);
            System.out.println("Ошибка подключения: " + e.getMessage());
        }
    }

    /**
     * Обрабатывает команду загрузки файла на сервер.
     *
     * @param parts аргументы команды
     * @param in    входной поток
     * @param out   выходной поток
     */
    private void handleUpload(String[] parts, InputStream in, OutputStream out) throws IOException {
        if (parts.length < 2) {
            System.out.println("Использование: UPLOAD <имя файла>");
            return;
        }
        String filename = parts[1].trim();
        Path file = clientDir.resolve(filename);

        // 1. Проверяем наличие файла на клиенте
        if (!Files.exists(file)) {
            System.out.println("Файл не найден: " + file.toAbsolutePath());
            return;
        }

        // 2. Отправляем команду на сервер
        IoUtils.writeLine(out, "UPLOAD " + filename);
        log.debug("→ ОТПРАВЛЕНО: UPLOAD {}", filename);

        // 3. Читаем ответ сервера (START или CONTINUE)
        String response = IoUtils.readLine(in);
        log.debug("← ПОЛУЧЕНО: {}", response);
        if (response == null || response.startsWith("ОШИБКА")) {
            System.out.println("Сервер: " + response);
            return;
        }

        // 4. Определяем смещение (offset) для возобновления передачи
        long offset = parseOffset(response);
        long fileSize = Files.size(file);
        long remaining = fileSize - offset;

        // 5. Отправляем ожидаемый размер оставшейся части файла
        IoUtils.writeLine(out, String.valueOf(remaining));
        log.debug("→ ОТПРАВЛЕНО: размер = {} байт", remaining);

        // 6. Отправляем содержимое файла (с пропуском offset байт)
        try (InputStream fis = Files.newInputStream(file)) {
            long skipped = fis.skip(offset);
            if (skipped < offset) {
                throw new IOException("Не удалось пропустить " + offset + " байт, фактически: " + skipped);
            }
            log.info("Загрузка '{}' ({} байт, смещение: {})", filename, fileSize, offset);
            IoUtils.copyStream(fis, out, remaining);
        }

        // 7. Читаем финальный ответ сервера (битрейт)
        String finalResponse = IoUtils.readLine(in);
        log.debug("← ПОЛУЧЕНО: {}", finalResponse);
        if (finalResponse != null) {
            System.out.println("Сервер: " + finalResponse);
        } else {
            System.out.println("Сервер закрыл соединение после загрузки");
        }
    }

    /**
     * Обрабатывает команду скачивания файла с сервера.
     *
     * @param parts аргументы команды
     * @param in    входной поток
     * @param out   выходной поток
     */
    private void handleDownload(String[] parts, InputStream in, OutputStream out) throws IOException {
        if (parts.length < 2) {
            System.out.println("Использование: DOWNLOAD <имя файла>");
            return;
        }
        String filename = parts[1].trim();

        // 1. Отправляем команду на сервер
        IoUtils.writeLine(out, "DOWNLOAD " + filename);
        log.debug("→ ОТПРАВЛЕНО: DOWNLOAD {}", filename);

        // 2. Читаем статус ответа (ОК или ОШИБКА)
        String status = IoUtils.readLine(in);
        log.debug("← ПОЛУЧЕНО: {}", status);
        if (status == null || status.startsWith("ОШИБКА")) {
            System.out.println("Сервер: " + status);
            return;
        }
        if (!"ОК".equals(status)) {
            System.out.println("Неожиданный ответ сервера: " + status);
            return;
        }

        // 3. Читаем размер файла
        String sizeLine = IoUtils.readLine(in);
        long fileSize = Long.parseLong(sizeLine.trim());
        Path target = clientDir.resolve("client_" + filename);

        // 4. Получаем файл и сохраняем его
        log.info("Скачивание '{}' ({} байт) в {}", filename, fileSize, target.getFileName());
        saveFileFromStream(in, target, fileSize);

        // 5. Читаем финальное сообщение сервера (битрейт)
        String finalMsg = IoUtils.readLine(in);
        if (finalMsg != null) {
            System.out.println("Сервер: " + finalMsg);
        }

        // 6. Сообщаем пользователю об успешном сохранении
        System.out.println("Файл сохранён: " + target.getFileName());
    }


    /**
     * Отправляет команду и читает ответ сервера.
     *
     * @param command команда
     * @param in      входной поток
     * @param out     выходной поток
     * @return ответ сервера или null
     */
    private String sendCommandAndReadResponse(String command, InputStream in, OutputStream out) throws IOException {
        IoUtils.writeLine(out, command);
        return IoUtils.readLine(in);
    }

    /**
     * Сохраняет файл из входного потока.
     *
     * @param in       входной поток
     * @param target   путь для сохранения
     * @param fileSize ожидаемый размер файла
     * @throws IOException при ошибках ввода-вывода
     */
    private void saveFileFromStream(InputStream in, Path target, long fileSize) throws IOException {
        try (OutputStream fos = Files.newOutputStream(target)) {
            IoUtils.copyStream(in, fos, fileSize);
        }
    }

    /**
     * Парсит смещение из ответа сервера.
     *
     * @param response строка ответа
     * @return смещение в байтах
     */
    private long parseOffset(String response) {
        if (response.startsWith("START ") || response.startsWith("CONTINUE ")) {
            try {
                return Long.parseLong(response.split(" ", 2)[1]);
            } catch (NumberFormatException e) {
                log.warn("Не удалось разобрать смещение из: {}", response);
            }
        }
        return 0;
    }
}
