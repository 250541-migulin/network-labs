// File: src/main/java/network/labs/lab4/server/ClientHandler.java
package network.labs.lab4.server;

// ИСПРАВЛЕНИЕ: используем утилиты из lab1.common, так как они универсальны
import network.labs.lab1.common.Config;
import network.labs.lab1.common.IoUtils;
import network.labs.lab1.common.NetworkUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

/**
 * Рабочий поток пула. Обрабатывает одного клиента от подключения до отключения.
 * Содержит полную логику команд и передачи файлов, вынесенную из TcpServer.
 */
public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    // Статические поля для отслеживания состояния докачки (упрощённо для лабы)
    private static InetAddress lastClientIp;
    private static String lastFilename;

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try (InputStream in = clientSocket.getInputStream();
             OutputStream out = clientSocket.getOutputStream()) {
            // Настройка сокета: keepalive и таймаут на чтение
            clientSocket.setKeepAlive(true);
            clientSocket.setSoTimeout(Config.SOCKET_TIMEOUT_MS);
            // Запуск основного цикла обработки команд
            processCommandLoop(in, out);
        } catch (SocketTimeoutException e) {
            System.out.println("Таймаут чтения. Клиент неактивен.");
        } catch (SocketException e) {
            System.out.println("Соединение разорвано: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Ошибка ввода-вывода: " + e.getMessage());
        } finally {
            closeClientSocket();
        }
    }

    /**
     * Цикл чтения команд клиента до получения CLOSE или обрыва соединения.
     */
    private void processCommandLoop(InputStream in, OutputStream out) throws IOException {
        String line;
        while ((line = IoUtils.readLine(in)) != null) {
            String cmd = line.split("\\s+", 2)[0].toUpperCase();
            switch (cmd) {
                case "ECHO"     -> handleEcho(line, out);
                case "TIME"     -> handleTime(out);
                case "CLOSE"    -> { handleClose(out); return; }
                case "UPLOAD"   -> handleUpload(line, in, out);
                case "DOWNLOAD" -> handleDownload(line, in, out);
                default         -> IoUtils.writeLine(out, "Error: unknown command");
            }
        }
    }

    private void handleEcho(String line, OutputStream out) throws IOException {
        String payload = line.length() > 4 ? line.substring(4).trim() : "";
        IoUtils.writeLine(out, payload);
    }

    private void handleTime(OutputStream out) throws IOException {
        String time = LocalDateTime.now().format(Config.TIME_FORMAT);
        IoUtils.writeLine(out, "Время: " + time);
    }

    private void handleClose(OutputStream out) throws IOException {
        IoUtils.writeLine(out, "Соединение закрыто");
    }

    /**
     * Координирует загрузку файла: парсит команду, вычисляет смещение,
     * запускает чтение потока и выводит результат.
     */
    private void handleUpload(String line, InputStream in, OutputStream out) throws IOException {
        String filename = parseFilename(line);
        if (filename == null) {
            IoUtils.writeLine(out, "ОШИБКА: имя файла не указано");
            return;
        }
        boolean force = hasForceFlag(line);
        Path target = Config.TMP_DIR.resolve(filename);
        long offset = getOffsetForResume(clientSocket.getInetAddress(), filename, target, force);

        IoUtils.writeLine(out, "OK " + offset);
        long remaining = parseRemainingSize(in);
        if (remaining <= 0) {
            IoUtils.writeLine(out, remaining == 0 ? "Файл уже загружен" : "Ошибка протокола");
            return;
        }

        executeFileTransfer(target, offset, remaining, in, out, "Докачка/Загрузка");
        lastClientIp = clientSocket.getInetAddress();
        lastFilename = filename;
        IoUtils.writeLine(out, "Файл загружен: " + filename);
    }

    /**
     * Координирует скачивание файла: проверяет наличие, вычисляет смещение,
     * запускает отправку потока и выводит результат.
     */
    private void handleDownload(String line, InputStream in, OutputStream out) throws IOException {
        String filename = parseFilename(line);
        if (filename == null) {
            IoUtils.writeLine(out, "ОШИБКА: имя файла не указано");
            return;
        }
        Path source = Config.SOURCE_DIR.resolve(filename);
        if (!Files.exists(source)) {
            IoUtils.writeLine(out, "ОШИБКА: файл не найден");
            return;
        }

        IoUtils.writeLine(out, "OK");
        long clientOffset = parseClientOffset(in);
        long fileSize = Files.size(source);
        long remaining = Math.max(0, fileSize - clientOffset);
        IoUtils.writeLine(out, String.valueOf(remaining));

        if (remaining == 0) {
            IoUtils.writeLine(out, "Файл уже актуален");
            return;
        }

        try (FileInputStream fileIn = new FileInputStream(source.toFile())) {
            fileIn.skipNBytes(clientOffset);
            executeFileTransfer(source, clientOffset, remaining, fileIn, out, "Отдача");
        }

        lastClientIp = clientSocket.getInetAddress();
        lastFilename = filename;
        IoUtils.writeLine(out, "Файл отправлен: " + filename);
    }

    /**
     * Универсальный метод передачи файла. Работает в обе стороны.
     * Для UPLOAD: читает из сокета, пишет в файл.
     * Для DOWNLOAD: читает из файла, пишет в сокет.
     */
    private void executeFileTransfer(Path path, long offset, long remaining,
                                     InputStream sourceStream, OutputStream destStream,
                                     String operationType) throws IOException {
        long total = 0;
        byte[] buf = new byte[8192];
        long startTime = System.currentTimeMillis();

        try {
            while (total < remaining) {
                int toRead = (int) Math.min(buf.length, remaining - total);
                int read = sourceStream.read(buf, 0, toRead);
                if (read == -1) break;
                destStream.write(buf, 0, read);
                destStream.flush();
                total += read;
            }
        } finally {
            long elapsed = System.currentTimeMillis() - startTime;
            double speed = NetworkUtils.calcSpeedMbps(total, elapsed);
            int percent = (total >= remaining) ? 100 : (int) ((total * 100) / remaining);

            System.out.printf("%s %s: %d байт | Прогресс: %d%% | Скорость: %.1f Мбит/с | Время: %d мс%n",
                    operationType,
                    path.getFileName(),
                    remaining,
                    percent,
                    speed,
                    elapsed);
        }
    }

    /**
     * Определяет смещение для докачки на основе IP, имени файла и флага --force.
     */
    private long getOffsetForResume(InetAddress ip, String fname, Path target, boolean force) throws IOException {
        if (force || !Files.exists(target)) return 0;
        boolean sameSession = lastClientIp != null
                && ip.getHostAddress().equals(lastClientIp.getHostAddress())
                && fname.equals(lastFilename);
        return sameSession ? Files.size(target) : 0;
    }

    private long parseRemainingSize(InputStream in) throws IOException {
        String line = IoUtils.readLine(in);
        try { return (line == null) ? -1 : Long.parseLong(line.trim()); }
        catch (NumberFormatException e) { return -1; }
    }

    private long parseClientOffset(InputStream in) throws IOException {
        String line = IoUtils.readLine(in);
        try { return (line == null) ? 0 : Long.parseLong(line.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private String parseFilename(String line) {
        String[] parts = line.split("\\s+", 2);
        return (parts.length >= 2) ? parts[1] : null;
    }

    private boolean hasForceFlag(String line) {
        for (String token : line.split("\\s+")) {
            if ("--force".equals(token) || "-f".equals(token)) return true;
        }
        return false;
    }

    private void closeClientSocket() {
        try { clientSocket.close(); } catch (IOException ignored) {}
    }
}