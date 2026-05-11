package network.labs.lab3.server;

import network.labs.lab3.common.Config;
import network.labs.lab3.common.NetworkUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Set;

/**
 * Мультиплексированный TCP-сервер на основе Java NIO Selector.
 * Логирует все команды клиентов, время выполнения и пропускную способность.
 */
public final class MultiplexedServer {

    private static final Logger log = LoggerFactory.getLogger(MultiplexedServer.class);
    private final Selector selector;

    public MultiplexedServer() throws IOException {
        this.selector = Selector.open();
    }

    public void start() throws IOException {
        ServerSocketChannel serverCh = ServerSocketChannel.open();
        serverCh.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        serverCh.bind(new InetSocketAddress(Config.SERVER_PORT));
        serverCh.configureBlocking(false);
        serverCh.register(selector, SelectionKey.OP_ACCEPT);

        log.info("Server started on port {}", Config.SERVER_PORT);

        while (true) {
            runSelectLoop();
        }
    }

    private void runSelectLoop() {
        try {
            if (selector.select(Config.SELECT_TIMEOUT_MS) == 0) return;

            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> it = keys.iterator();

            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove();

                if (!key.isValid()) continue;

                try {
                    if (key.isAcceptable()) handleAccept((ServerSocketChannel) key.channel());
                    else if (key.isReadable()) handleRead(key);
                    else if (key.isWritable()) handleWrite(key);
                } catch (IOException e) {
                    log.warn("IO error: {}", e.getMessage());
                    disconnect(key, "IO error");
                }
            }
        } catch (IOException e) {
            log.error("Selector error: {}", e.getMessage());
        }
    }

    private void handleAccept(ServerSocketChannel ssc) throws IOException {
        SocketChannel ch = ssc.accept();
        if (ch == null) return;

        ch.configureBlocking(false);
        ClientSession session = new ClientSession(ch);
        ch.register(selector, SelectionKey.OP_READ, session);

        log.info("[{}] Connected from {}", session.id, ch.getRemoteAddress());
    }

    private void handleRead(SelectionKey key) throws IOException {
        ClientSession s = (ClientSession) key.attachment();
        int read = s.channel.read(s.netBuf);
        if (read == -1) { disconnect(key, "EOF"); return; }
        if (read == 0) return; // Нет данных

        s.netBuf.flip();
        try {
            switch (s.state) {
                case IDLE -> handleTextCommands(key, s);
                case WAITING_UPLOAD_SIZE, WAITING_DOWNLOAD_SIZE -> handleSizeLine(key, s);
                case UPLOADING -> handleBinaryUpload(key, s);
                case DOWNLOADING -> {} // Обрабатывается в handleWrite
            }
        } finally {
            s.netBuf.compact();
        }
    }

    private void handleTextCommands(SelectionKey key, ClientSession s) {
        s.commandBuffer.append(StandardCharsets.UTF_8.decode(s.netBuf).toString());
        processPendingCommands(key, s);
    }

    private void processPendingCommands(SelectionKey key, ClientSession s) {
        String buffer = s.commandBuffer.toString();
        int idx;
        while ((idx = buffer.indexOf(Config.LINE_END)) != -1) {
            String line = buffer.substring(0, idx);
            buffer = buffer.substring(idx + Config.LINE_END.length());
            executeCommand(key, s, line);
        }
        s.commandBuffer.setLength(0);
        if (!buffer.isEmpty()) s.commandBuffer.append(buffer);
    }

    private void executeCommand(SelectionKey key, ClientSession s, String line) {
        log.info("[{}] CMD: {}", s.id, line);
        try {
            String[] tokens = line.split("\\s+", 2);
            String cmd = tokens[0].toUpperCase();

            switch (cmd) {
                case "ECHO" -> sendResponse(key, tokens.length > 1 ? tokens[1] : "");
                case "TIME" -> sendResponse(key, "Time: " + LocalDateTime.now().format(Config.TIME_FMT));
                case "CLOSE" -> disconnect(key, "client request");
                case "UPLOAD" -> {
                    if (tokens.length < 2) { sendResponse(key, "Error: missing filename"); return; }
                    if (s.uploadFileCh != null && s.uploadFileCh.isOpen()) s.uploadFileCh.close();
                    s.filePath = Config.TMP_DIR.resolve(tokens[1]);
                    Files.createDirectories(s.filePath.getParent());
                    s.uploadFileCh = FileChannel.open(s.filePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                    s.state = ClientSession.State.WAITING_UPLOAD_SIZE;
                    sendResponse(key, "OK 0");
                }
                case "DOWNLOAD" -> {
                    if (tokens.length < 2) { sendResponse(key, "Error: missing filename"); return; }
                    Path src = Config.SOURCE_DIR.resolve(tokens[1]);
                    if (!Files.exists(src)) { sendResponse(key, "Error: file not found"); return; }
                    s.filePath = src;
                    s.state = ClientSession.State.WAITING_DOWNLOAD_SIZE;
                    sendResponse(key, "OK");
                }
                default -> sendResponse(key, "Error: unknown command");
            }
        } catch (IOException e) {
            log.error("[{}] Command error: {}", s.id, e.getMessage());
            disconnect(key, "command error");
        }
    }

    private void handleSizeLine(SelectionKey key, ClientSession s) throws IOException {
        String line = extractLine(s.netBuf);
        if (line == null) return; // Строка ещё не получена полностью

        try {
            if (s.state == ClientSession.State.WAITING_UPLOAD_SIZE) {
                s.bytesExpected = Long.parseLong(line.trim());
                s.bytesProcessed = 0;
                s.state = ClientSession.State.UPLOADING;
                s.transferStartTime = System.currentTimeMillis();

                if (s.bytesExpected == 0) {
                    s.uploadFileCh.close();
                    sendResponse(key, "File uploaded: " + s.filePath.getFileName());
                    log.info("[{}] UPLOAD completed: {} (0 bytes)", s.id, s.filePath.getFileName());
                    s.reset(); key.interestOps(SelectionKey.OP_READ);
                } else {
                    log.info("[{}] UPLOAD started: {} ({} bytes expected)", s.id, s.filePath.getFileName(), s.bytesExpected);
                }
            } else if (s.state == ClientSession.State.WAITING_DOWNLOAD_SIZE) {
                long clientOffset = Long.parseLong(line.trim());
                long fileSize = Files.size(s.filePath);
                s.bytesExpected = Math.max(0, fileSize - clientOffset);
                s.bytesProcessed = 0;

                if (s.bytesExpected == 0) {
                    sendResponse(key, "File is already up to date");
                    s.reset();
                } else {
                    s.downloadFileCh = FileChannel.open(s.filePath, StandardOpenOption.READ);
                    s.downloadFileCh.position(clientOffset);
                    s.state = ClientSession.State.DOWNLOADING;
                    s.transferStartTime = System.currentTimeMillis();
                    key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                    sendResponse(key, String.valueOf(s.bytesExpected));
                    log.info("[{}] DOWNLOAD started: {} ({} bytes from offset {})", s.id, s.filePath.getFileName(), s.bytesExpected, clientOffset);
                }
            }
        } catch (NumberFormatException e) {
            sendResponse(key, "Error: invalid number"); disconnect(key, "parse error");
        } catch (IOException e) {
            sendResponse(key, "Error: file access failed"); disconnect(key, "file error");
        }
    }

    private void handleBinaryUpload(SelectionKey key, ClientSession s) throws IOException {
        if (s.uploadFileCh == null || s.bytesProcessed >= s.bytesExpected) return;
        long written = s.uploadFileCh.write(s.netBuf);
        s.bytesProcessed += written;
        if (s.bytesProcessed >= s.bytesExpected) {
            s.uploadFileCh.close();
            finishUpload(key, s);
        }
    }

    private void handleWrite(SelectionKey key) throws IOException {
        ClientSession s = (ClientSession) key.attachment();
        if (s.state != ClientSession.State.DOWNLOADING || s.downloadFileCh == null) return;

        if (!s.netBuf.hasRemaining()) {
            s.netBuf.clear();
            int read = s.downloadFileCh.read(s.netBuf);
            if (read == -1) { finishDownload(key, s); return; }
            s.netBuf.flip();
        }

        int written = s.channel.write(s.netBuf);
        if (written == 0) return; // Сокет временно не готов к записи

        s.bytesProcessed += written;
        if (s.bytesProcessed >= s.bytesExpected) { finishDownload(key, s); }
    }

    private void finishUpload(SelectionKey key, ClientSession s) throws IOException {
        long duration = System.currentTimeMillis() - s.transferStartTime;
        double speed = NetworkUtils.calcSpeedMbps(s.bytesProcessed, duration);
        sendResponse(key, "File uploaded: " + s.filePath.getFileName());
        log.info("[{}] UPLOAD completed: {} ({} bytes) in {} ms ({:.1f} Mbps)", s.id, s.filePath.getFileName(), s.bytesProcessed, duration, speed);
        s.reset(); key.interestOps(SelectionKey.OP_READ);
    }

    private void finishDownload(SelectionKey key, ClientSession s) throws IOException {
        long duration = System.currentTimeMillis() - s.transferStartTime;
        double speed = NetworkUtils.calcSpeedMbps(s.bytesProcessed, duration);
        sendResponse(key, "File sent: " + s.filePath.getFileName());
        log.info("[{}] DOWNLOAD completed: {} ({} bytes) in {} ms ({:.1f} Mbps)", s.id, s.filePath.getFileName(), s.bytesProcessed, duration, speed);
        s.reset(); key.interestOps(SelectionKey.OP_READ);
    }

    private String extractLine(ByteBuffer buf) {
        buf.mark();
        while (buf.hasRemaining()) {
            byte b = buf.get();
            if (b == '\r' && buf.hasRemaining() && buf.get() == '\n') {
                int limit = buf.position(); buf.reset();
                byte[] lineBytes = new byte[limit - buf.position() - 1];
                buf.get(lineBytes); buf.get();
                return new String(lineBytes, StandardCharsets.UTF_8);
            }
        }
        buf.reset(); return null;
    }

    private void sendResponse(SelectionKey key, String text) throws IOException {
        ClientSession s = (ClientSession) key.attachment();
        byte[] data = (text + Config.LINE_END).getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.wrap(data);
        int attempts = 0;
        // Гарантируем запись короткого текстового ответа в неблокирующем режиме
        while (buf.hasRemaining() && attempts < 100) { s.channel.write(buf); attempts++; }
        if (buf.hasRemaining()) disconnect(key, "send buffer overflow");
    }

    private void disconnect(SelectionKey key, String reason) {
        try {
            ClientSession s = (ClientSession) key.attachment();
            if (key.channel() instanceof SocketChannel) {
                log.info("[{}] Disconnected: {}", s != null ? s.id : "?", reason);
            }
        } catch (Exception ignored) {}
        try {
            ClientSession s = (ClientSession) key.attachment();
            if (s != null) s.reset();
            key.cancel(); key.channel().close();
        } catch (IOException ignored) {}
    }
}