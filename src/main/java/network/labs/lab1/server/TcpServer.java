package network.labs.lab1.server;

import network.labs.lab1.common.*;
import network.labs.lab1.server.commands.*;
import network.labs.lab2.util.PathsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Path;

/**
 * Последовательный TCP-сервер (ЛР №1).
 */
public class TcpServer {
    private static final Logger log = LoggerFactory.getLogger(TcpServer.class);
    private final int port;
    private final Path serverDir;

    public TcpServer(int port) {
        this.port = port;
        this.serverDir = PathsConfig.SERVER_TCP;
        FileUtils.ensureDirectory(serverDir);
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log.info("TCP-сервер запущен на порту {}", port);

            while (true) {
                Socket client = serverSocket.accept();
                log.info("Подключился клиент: {}", client.getRemoteSocketAddress());

                client.setKeepAlive(true);
                client.setSoTimeout(120_000);

                handleClient(client);
                log.info("Клиент отключился");
            }
        } catch (IOException e) {
            log.error("Сервер остановлен", e);
        }
    }

    private void handleClient(Socket client) {
        try (client) {
            TcpServerCommandContext ctx = new TcpServerCommandContext(client, serverDir);

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
        reg.register(new UploadCommand(serverDir));
        reg.register(new DownloadCommand(serverDir));
        return reg;
    }
}