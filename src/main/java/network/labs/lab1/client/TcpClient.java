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

    public TcpClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.clientDir = Path.of("client_files");
        initClientDirectory();
    }

    private void initClientDirectory() {
        try {
            Files.createDirectories(clientDir);
            log.info("Client directory ensured: {}", clientDir.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to initialize client directory", e);
            throw new IllegalStateException("Client directory unavailable", e);
        }
    }

    public void start() {
        log.info("Connecting to {}:{}", host, port);
        try (Socket socket = new Socket(host, port)) {
            socket.setKeepAlive(true);
            log.info("Connected to {}:{}", host, port);

            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            Scanner scanner = new Scanner(System.in, "UTF-8");

            System.out.println("Connected to " + host + ":" + port);
            System.out.println("Commands: ECHO <text>, TIME, CLOSE, UPLOAD/DOWNLOAD <file>");

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
                            System.out.println("Server: " + (response != null ? response : "Connection closed"));
                            running = false;
                        }
                        default -> {
                            IoUtils.writeLine(out, line);
                            String response = IoUtils.readLine(in);
                            if (response == null) {
                                System.out.println("Server closed connection");
                                running = false;
                            } else {
                                System.out.println("Server: " + response);
                            }
                        }
                    }
                } catch (IOException e) {
                    log.error("Command execution failed", e);
                    System.out.println("Error: " + e.getMessage());
                    running = false;
                }
            }

        } catch (IOException e) {
            log.error("Failed to connect to {}:{}]", host, port, e);
            System.out.println("Connection failed: " + e.getMessage());
        }
    }

    private void handleUpload(String[] parts, InputStream in, OutputStream out) throws IOException {
        if (parts.length < 2) {
            System.out.println("Usage: UPLOAD <filename>");
            return;
        }
        String filename = parts[1].trim();
        Path file = clientDir.resolve(filename);

        if (!Files.exists(file)) {
            System.out.println("File not found: " + file.toAbsolutePath());
            return;
        }

        // 1. Отправляем команду
        IoUtils.writeLine(out, "UPLOAD " + filename);
        log.debug("→ SENT: UPLOAD {}", filename);

        // 2. Читаем ответ: START или CONTINUE
        String response = IoUtils.readLine(in);
        log.debug("← RECV: {}", response);
        if (response == null || response.startsWith("ERROR")) {
            System.out.println("Server: " + response);
            return;
        }

        // 3. Парсим смещение
        long offset = parseOffset(response);
        long fileSize = Files.size(file);

        // ✅ 4. Отправляем ОЖИДАЕМЫЙ размер (оставшуюся часть файла)
        long remaining = fileSize - offset;
        IoUtils.writeLine(out, String.valueOf(remaining));
        log.debug("→ SENT: file size = {} bytes", remaining);

        // 5. Отправляем файл (с пропуском offset байт)
        try (InputStream fis = Files.newInputStream(file)) {
            fis.skip(offset);
            log.info("Uploading '{}' ({} bytes, offset: {})", filename, fileSize, offset);
            IoUtils.copyStream(fis, out, remaining);
        }

        // ✅ 6. Читаем финальный ответ сервера
        String finalResponse = IoUtils.readLine(in);
        log.debug("← RECV: {}", finalResponse);
        if (finalResponse != null) {
            System.out.println("Server: " + finalResponse);
        } else {
            System.out.println("Server closed connection after upload");
        }
    }

    private void handleDownload(String[] parts, InputStream in, OutputStream out) throws IOException {
        if (parts.length < 2) {
            System.out.println("Usage: DOWNLOAD <filename>");
            return;
        }
        String filename = parts[1].trim();

        // 1. Отправляем команду
        IoUtils.writeLine(out, "DOWNLOAD " + filename);
        log.debug("→ SENT: DOWNLOAD {}", filename);

        // 2. Читаем статус
        String status = IoUtils.readLine(in);
        log.debug("← RECV: {}", status);
        if (status == null || status.startsWith("ERROR")) {
            System.out.println("Server: " + status);
            return;
        }

        if (!"OK".equals(status)) {
            System.out.println("Unexpected server response: " + status);
            return;
        }

        // 3. Читаем размер
        String sizeLine = IoUtils.readLine(in);
        long fileSize = Long.parseLong(sizeLine.trim());
        Path target = clientDir.resolve("client_" + filename);

        // 4. Получаем файл
        log.info("Downloading '{}' ({} bytes) to {}", filename, fileSize, target.getFileName());
        try (OutputStream fos = Files.newOutputStream(target)) {
            IoUtils.copyStream(in, fos, fileSize);
        }

        // ✅ 5. В DOWNLOAD финального ответа НЕТ — сервер сразу переходит к следующей команде
        // (передача файла — последнее действие в рамках команды)
        System.out.println("File saved: " + target.getFileName());
    }

    private long parseOffset(String response) {
        if (response.startsWith("START ") || response.startsWith("CONTINUE ")) {
            try {
                return Long.parseLong(response.split(" ", 2)[1]);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse offset from: {}", response);
            }
        }
        return 0;
    }
}