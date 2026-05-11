package network.labs.lab1.server;

import network.labs.lab1.common.Config;
import network.labs.lab1.common.IoUtils;
import network.labs.lab1.common.NetworkUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class TcpServer {
    private InetAddress lastClientIp;
    private String lastFilename;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(Config.SERVER_PORT, 1)) {
            System.out.println("Сервер запущен на порту " + Config.SERVER_PORT);
            while (true) acceptAndHandleClient(serverSocket);
        } catch (IOException e) {
            System.err.println("Ошибка сервера: " + e.getMessage());
        }
    }

    private void acceptAndHandleClient(ServerSocket ss) {
        try {
            Socket client = ss.accept();
            client.setKeepAlive(true);
            client.setSoTimeout(Config.SOCKET_TIMEOUT_MS);
            InetAddress ip = client.getInetAddress();
            System.out.println("Подключился клиент: " + ip);
            handleClient(client, ip);
        } catch (SocketTimeoutException e) {
            System.out.println("Клиент неактивен более " + (Config.SOCKET_TIMEOUT_MS / 1000) + " сек — соединение закрыто");
        } catch (SocketException e) {
            System.out.println("Клиент разорвал соединение: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Ошибка при работе с клиентом: " + e.getMessage());
        }
    }

    private void handleClient(Socket client, InetAddress ip) throws IOException {
        try (client;
             InputStream in = client.getInputStream();
             OutputStream out = client.getOutputStream()) {
            String line;
            while ((line = IoUtils.readLine(in)) != null) {
                String cmd = extractCommand(line);
                switch (cmd) {
                    case "ECHO"     -> handleEcho(line, out);
                    case "TIME"     -> handleTime(out);
                    case "CLOSE"    -> { handleClose(out); return; }
                    case "UPLOAD"   -> handleUpload(line, in, out, ip);
                    case "DOWNLOAD" -> handleDownload(line, in, out, ip);
                    default         -> IoUtils.writeLine(out, "Error: unknown command");
                }
            }
        }
    }

    private String extractCommand(String line) { return line.split("\\s+")[0].toUpperCase(); }
    private void handleEcho(String line, OutputStream out) throws IOException {
        IoUtils.writeLine(out, line.length() > 4 ? line.substring(4).trim() : "");
    }
    private void handleTime(OutputStream out) throws IOException {
        IoUtils.writeLine(out, "Время: " + LocalDateTime.now().format(TIME_FMT));
    }
    private void handleClose(OutputStream out) throws IOException {
        lastClientIp = null; lastFilename = null;
        IoUtils.writeLine(out, "Соединение закрыто");
    }

    private void handleUpload(String line, InputStream in, OutputStream out, InetAddress ip) throws IOException {
        String filename = parseFilename(line);
        if (filename == null) { IoUtils.writeLine(out, "ОШИБКА: имя файла не указано"); return; }
        boolean force = hasForceFlag(line);
        Path target = Config.TMP_DIR.resolve(filename);
        long offset = calculateUploadOffset(ip, filename, target, force);

        IoUtils.writeLine(out, "OK " + offset);
        long remaining = readRemainingSize(in);
        if (remaining <= 0) {
            IoUtils.writeLine(out, remaining == 0 ? "Файл уже загружен" : "Ошибка протокола");
            return;
        }

        if (offset > 0) {
            System.out.println("Докачка"  + filename + " (уже есть: " + offset + " байт, осталось: " + remaining + " байт, всего: " + (offset + remaining) + " байт)...");
        } else {
            System.out.println("Новая загрузка" + filename + "Всего байт " + remaining);
        }

        try {
            long received = IoUtils.copyStreamToFile(in, target, offset > 0, remaining);
            System.out.println("Файл получен: " + filename + " (" + received + " байт)");
            lastClientIp = ip; lastFilename = filename;
            IoUtils.writeLine(out, "Файл загружен: " + filename);
        } catch (SocketTimeoutException e) {
            long received = Files.exists(target) ? Files.size(target) : 0;
            int percent = (int) ((received * 100) / (offset + remaining));
            System.out.println("Передача прервана: " + e.getMessage());
            System.out.println("Принято до обрыва: " + percent + "% (" + received + "/" + (offset + remaining) + " байт)");
            try { IoUtils.writeLine(out, "ERROR: Соединение разорвано"); } catch (IOException ignored) {}
        } catch (IOException e) {
            System.out.println("Ошибка передачи: " + e.getMessage());
            try { IoUtils.writeLine(out, "ERROR: Ошибка сети"); } catch (IOException ignored) {}
        }
    }

    /**
     * Обработка DOWNLOAD. Синхронизация с клиентом по шагам протокола.
     */
    private void handleDownload(String line, InputStream in, OutputStream out, InetAddress ip) throws IOException {
        String fname = parseFilename(line);
        if (fname == null) { IoUtils.writeLine(out, "ОШИБКА: имя файла не указано"); return; }

        Path source = Config.SOURCE_DIR.resolve(fname);
        if (!Files.exists(source)) { IoUtils.writeLine(out, "ОШИБКА: файл не найден"); return; }

        IoUtils.writeLine(out, "OK");
        long offset = readClientOffset(in);
        long fileSize = Files.size(source);
        long remaining = Math.max(0, fileSize - offset);

        // Отправляем клиенту размер оставшихся данных
        IoUtils.writeLine(out, String.valueOf(remaining));

        // Если скачивать нечего, сразу шлём статус и завершаем обработку
        if (remaining == 0) {
            IoUtils.writeLine(out, "Файл уже актуален");
            System.out.println("Файл уже актуален у клиента");
            return;
        }

        System.out.printf("Отдача: %s (пропущено: %,d байт, осталось: %,d байт)%n",
                fname, offset, remaining);

        try {
            long sent = IoUtils.copyFileToStream(source, out, offset);
            lastClientIp = ip; lastFilename = fname;
            IoUtils.writeLine(out, "Файл отправлен: " + fname);
        } catch (SocketTimeoutException e) {
            System.out.println("Передача прервана: " + e.getMessage());
            try { IoUtils.writeLine(out, "ERROR: Таймаут"); } catch (IOException ignored) {}
        } catch (IOException e) {
            System.out.println("Ошибка сети: " + e.getMessage());
            try { IoUtils.writeLine(out, "ERROR: Ошибка сети"); } catch (IOException ignored) {}
        }
    }

    private long readRemainingSize(InputStream in) throws IOException {
        String s = IoUtils.readLine(in);
        String trimmed = (s == null) ? "" : s.trim();
        if (trimmed.isEmpty() || !trimmed.chars().allMatch(Character::isDigit)) {
            System.out.println("Протокол: ожидался размер, получено '" + s + "'");
            return -1;
        }
        try { return Long.parseLong(trimmed); } catch (NumberFormatException e) { return -1; }
    }

    private long readClientOffset(InputStream in) throws IOException {
        String s = IoUtils.readLine(in);
        String trimmed = (s == null) ? "" : s.trim();
        if (trimmed.isEmpty() || !trimmed.chars().allMatch(Character::isDigit)) {
            System.out.println("Протокол: ожидалось смещение, получено '" + s + "'");
            return -1;
        }
        try { return Long.parseLong(trimmed); } catch (NumberFormatException e) { return -1; }
    }

    private String parseFilename(String line) {
        String[] p = line.split("\\s+", 2);
        return p.length >= 2 ? p[1] : null;
    }
    private boolean hasForceFlag(String line) {
        for (String t : line.split("\\s+")) if ("--force".equals(t) || "-f".equals(t)) return true;
        return false;
    }
    private long calculateUploadOffset(InetAddress ip, String fname, Path tgt, boolean force) throws IOException {
        if (force) { if (Files.exists(tgt)) Files.delete(tgt); return 0; }
        boolean same = lastClientIp != null && ip.getHostAddress().equals(lastClientIp.getHostAddress()) && fname.equals(lastFilename);
        if (same && Files.exists(tgt)) return Files.size(tgt);
        if (Files.exists(tgt)) Files.delete(tgt);
        return 0;
    }
}