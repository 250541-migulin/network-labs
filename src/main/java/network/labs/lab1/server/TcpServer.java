package network.labs.lab1.server;

import network.labs.lab1.common.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Последовательный TCP-сервер для лабораторной работы №1.
 * Поддерживает команды: ECHO, TIME, CLOSE/QUIT/EXIT, UPLOAD, DOWNLOAD.
 */
public class TcpServer {
    private static final Logger log = LoggerFactory.getLogger(TcpServer.class);

    private final int port;
    private final Path serverDir;
    private final Map<String, CommandSpec> commandRegistry;

    public TcpServer(int port) {
        this.port = port;
        this.serverDir = Path.of("server_files");
        this.commandRegistry = new HashMap<>();
        initServerDirectory();
        registerCommands();
    }

    private void initServerDirectory() {
        try {
            Files.createDirectories(serverDir);
            log.info("Server directory ensured: {}", serverDir.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to initialize server directory", e);
            throw new IllegalStateException("Server directory unavailable", e);
        }
    }

    public void start() {
        log.info("Starting server on port {}", port);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log.info("Server is listening on port {}", port);

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setKeepAlive(true);
                    handleClient(clientSocket);
                } catch (IOException e) {
                    log.warn("Failed to accept client connection", e);
                }
            }
        } catch (IOException e) {
            log.error("Server socket error", e);
        }
    }

    private void handleClient(Socket clientSocket) {
        String clientAddr = clientSocket.getRemoteSocketAddress().toString();
        log.info("New client connected: {}", clientAddr);

        try (clientSocket) {
            CommandContext ctx = new CommandContext(clientSocket);
            processClientCommands(ctx, clientAddr);
        } catch (IOException e) {
            log.warn("Client connection terminated unexpectedly: {}", clientAddr, e);
        } finally {
            log.info("Client disconnected: {}", clientAddr);
        }
    }

    private void processClientCommands(CommandContext ctx, String clientAddr) throws IOException {
        String commandLine;
        while ((commandLine = IoUtils.readLine(ctx.in())) != null) {
            log.debug("[{}] Received: {}", clientAddr, commandLine);
            if (executeCommand(commandLine, ctx)) {
                break;
            }
        }
    }

    private boolean executeCommand(String line, CommandContext ctx) {
        String[] parts = line.trim().split("\\s+", 2);
        if (parts.length == 0) {
            return false;
        }

        String commandName = parts[0].toUpperCase();
        String[] args = parts.length > 1 ? new String[]{parts[1].trim()} : new String[0];

        CommandSpec spec = commandRegistry.get(commandName);
        if (spec == null) {
            sendResponse(ctx, "ERROR Unknown command: " + commandName);
            return false;
        }

        if (!spec.validate(args)) {
            sendResponse(ctx, String.format(
                    "ERROR Command %s requires %d to %d arguments",
                    commandName, spec.minArgs(), spec.maxArgs()
            ));
            return false;
        }

        return spec.handler().handle(args, ctx);
    }

    private void sendResponse(CommandContext ctx, String message) {
        try {
            IoUtils.writeLine(ctx.out(), message);
        } catch (IOException e) {
            log.warn("Failed to send response", e);
        }
    }

    private void registerCommands() {
        commandRegistry.put("ECHO", new CommandSpec(0, Integer.MAX_VALUE, (args, ctx) -> {
            String response = String.join(" ", args);
            sendResponse(ctx, response);
            return false;
        }));

        commandRegistry.put("TIME", new CommandSpec(0, 0, (args, ctx) -> {
            sendResponse(ctx, LocalDateTime.now().toString());
            return false;
        }));

        CommandHandler closeHandler = (args, ctx) -> {
            sendResponse(ctx, "Connection closed");
            return true;
        };
        commandRegistry.put("CLOSE", new CommandSpec(0, 0, closeHandler));
        commandRegistry.put("QUIT", new CommandSpec(0, 0, closeHandler));
        commandRegistry.put("EXIT", new CommandSpec(0, 0, closeHandler));

        commandRegistry.put("UPLOAD", new CommandSpec(1, 1, this::handleUpload));
        commandRegistry.put("DOWNLOAD", new CommandSpec(1, 1, this::handleDownload));
    }

    private boolean handleUpload(String[] args, CommandContext ctx) {
        String filename = args[0];
        String clientAddr = ctx.socket().getRemoteSocketAddress().toString();
        Path target = serverDir.resolve(filename);
        Path partFile = serverDir.resolve(filename + ".part");
        Path resumeInfo = serverDir.resolve(filename + ".resume");

        try {
            // 1. Подготавливаем resume
            UploadResumeState state = prepareUploadResume(partFile, resumeInfo, clientAddr);
            sendResponse(ctx, (state.isResume() ? "CONTINUE " : "START ") + state.offset());

            // ✅ 2. Читаем ОЖИДАЕМЫЙ размер (от клиента)
            String sizeLine = IoUtils.readLine(ctx.in());
            long remaining = Long.parseLong(sizeLine.trim());
            log.debug("[{}] Expected file size: {} bytes", clientAddr, remaining);

            // 3. Принимаем ровно 'remaining' байт
            long start = System.nanoTime();
            long received = receiveFileContent(ctx, partFile, state.isResume(), remaining);
            long end = System.nanoTime();

            // 4. Финализируем
            finalizeUpload(partFile, target, resumeInfo, filename);

            // 5. Отправляем результат
            double bitrate = calculateBitrate(received, end - start);
            String response = String.format("File '%s' uploaded (%d bytes, %.2f KB/s)", filename, received, bitrate);
            sendResponse(ctx, response);

            log.info("Upload completed: {} ← {} ({} bytes, {:.2f} KB/s)", filename, clientAddr, received, bitrate);

        } catch (IOException e) {
            log.error("UPLOAD failed for file: {}", filename, e);
            sendResponse(ctx, "ERROR Upload failed: " + e.getMessage());
        }
        return false;
    }

    private UploadResumeState prepareUploadResume(Path partFile, Path resumeInfo, String clientAddr) throws IOException {
        if (Files.exists(partFile) && Files.exists(resumeInfo)) {
            String savedClient = Files.readString(resumeInfo, StandardCharsets.UTF_8).trim();
            if (savedClient.equals(clientAddr)) {
                return new UploadResumeState(true, Files.size(partFile));
            } else {
                Files.deleteIfExists(partFile);
                Files.deleteIfExists(resumeInfo);
            }
        }
        Files.writeString(resumeInfo, clientAddr, StandardCharsets.UTF_8);
        return new UploadResumeState(false, 0);
    }

    private long receiveFileContent(CommandContext ctx, Path partFile, boolean resume, long length) throws IOException {
        try (OutputStream out = Files.newOutputStream(partFile,
                StandardOpenOption.CREATE,
                resume ? StandardOpenOption.APPEND : StandardOpenOption.WRITE)) {
            return IoUtils.copyStream(ctx.in(), out, length);
        }
    }

    private void finalizeUpload(Path partFile, Path target, Path resumeInfo, String filename) throws IOException {
        Files.move(partFile, target, StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(resumeInfo);
    }

    private boolean handleDownload(String[] args, CommandContext ctx) {
        String filename = args[0];
        Path source = serverDir.resolve(filename);
        String clientAddr = ctx.socket().getRemoteSocketAddress().toString();

        if (!Files.exists(source)) {
            sendResponse(ctx, "ERROR File not found: " + filename);
            return false;
        }

        try {
            sendResponse(ctx, "OK");
            IoUtils.writeLine(ctx.out(), String.valueOf(Files.size(source)));

            long start = System.nanoTime();
            long sent = IoUtils.copyStream(Files.newInputStream(source), ctx.out(), Files.size(source));
            long end = System.nanoTime();

            if (sent == Files.size(source)) {
                double bitrate = calculateBitrate(sent, end - start);
                log.info("Download completed: {} → {} ({} bytes, {:.2f} KB/s)", filename, clientAddr, sent, bitrate);
            } else {
                log.warn("Partial download: {}/{} bytes sent to {}", sent, Files.size(source), clientAddr);
            }

        } catch (IOException e) {
            log.error("DOWNLOAD failed for file: {}", filename, e);
            sendResponse(ctx, "ERROR Download failed: " + e.getMessage());
        }
        return false;
    }

    private double calculateBitrate(long bytes, long nanos) {
        double seconds = nanos / 1_000_000_000.0;
        return (bytes / 1024.0) / seconds;
    }

    private record UploadResumeState(boolean isResume, long offset) {}
}