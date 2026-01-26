package network.labs.lab1.server;

import network.labs.lab1.common.*;
import network.labs.lab1.server.commands.*;
import network.labs.lab2.util.PathsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Последовательный TCP-сервер (ЛР №1) с поддержкой докачки.
 */
public class TcpServer {
    private static final Logger log = LoggerFactory.getLogger(TcpServer.class);
    private final int port;
    private final Path serverDir;

    // Состояние для восстановления докачки
    private InetAddress lastClientIp = null;
    private String lastFilename = null;

    public TcpServer(int port) {
        this.port = port;
        this.serverDir = PathsConfig.SERVER_TCP;
        FileUtils.ensureDirectory(serverDir);

        // Очищаем директорию при старте сервера
        try {
            Files.list(serverDir).forEach(path -> {
                try {
                    Files.delete(path);
                    log.info("Удалён старый файл после перезапуска: {}", path.getFileName());
                } catch (IOException e) {
                    log.warn("Не удалось удалить файл: {}", path, e);
                }
            });
        } catch (IOException e) {
            log.error("Ошибка очистки директории", e);
        }
    }

    public void start() {
        ServerSocket serverSocket = null;
        try {
            // Создаём с минимальной очередью
            serverSocket = new ServerSocket(port, 1);
            log.info("TCP-сервер запущен на порту {}", port);

            while (true) {
                Socket client = serverSocket.accept();
                InetAddress clientIp = client.getInetAddress();
                log.info("Подключился клиент: {} (IP: {})", client.getRemoteSocketAddress(), clientIp);

                // Закрываем серверный сокет — новые подключения будут отклонены (по заданию последовательный сервер)
                serverSocket.close();

                try {
                    client.setKeepAlive(true);
                    client.setSoTimeout(120_000);
                    handleClient(client, clientIp);
                    log.info("Клиент отключился");
                } finally {
                    // Гарантируем, что клиент закрыт
                    try { client.close(); } catch (IOException ignored) {}
                }

                // 🔁 Снова открываем серверный сокет для следующего клиента
                serverSocket = new ServerSocket(port, 1);
            }
        } catch (IOException e) {
            log.error("Сервер остановлен", e);
        } finally {
            if (serverSocket != null) {
                try { serverSocket.close(); } catch (IOException ignored) {}
            }
        }
    }

    public void handleClient(Socket client, InetAddress clientIp) {
        try (client) {
            TcpServerCommandContext ctx = new TcpServerCommandContext(client, serverDir, clientIp);
            CommandRegistry<CommandContext> textReg = createTextRegistry();
            CommandRegistry<FileAwareContext> fileReg = createFileRegistry();

            while (true) {
                String line = ctx.readLine();
                if (line == null) break;

                String cmd = line.split("\\s+")[0].toUpperCase();
                CommandResult res;
                if ("UPLOAD".equals(cmd) || "DOWNLOAD".equals(cmd)) {
                    res = fileReg.dispatch(line, ctx);
                } else {
                    res = textReg.dispatch(line, ctx);
                }
                if (res == CommandResult.CLOSE) break;
            }
        } catch (SocketException e) {
            log.info("Клиент разорвал соединение: {}", e.getMessage());
        } catch (IOException e) {
            log.error("Ошибка обработки клиента", e);
        }
    }

    // --- Методы для докачки ---
    public boolean isSameClientAndFile(InetAddress ip, String filename) {
        return lastClientIp != null
                && lastClientIp.equals(ip)
                && lastFilename != null
                && lastFilename.equals(filename);
    }

    public void setLastSession(InetAddress ip, String filename) {
        this.lastClientIp = ip;
        this.lastFilename = filename;
    }

    private CommandRegistry<CommandContext> createTextRegistry() {
        CommandRegistry<CommandContext> reg = new CommandRegistry<>();
        reg.register(new EchoCommand());
        reg.register(new TimeCommand());
        reg.register(new CloseCommand());
        reg.register(new UnknownCommand());
        return reg;
    }

    private CommandRegistry<FileAwareContext> createFileRegistry() {
        CommandRegistry<FileAwareContext> reg = new CommandRegistry<>();
        reg.register(new UploadCommand(serverDir, this));
        reg.register(new DownloadCommand(serverDir, this));
        return reg;
    }
}