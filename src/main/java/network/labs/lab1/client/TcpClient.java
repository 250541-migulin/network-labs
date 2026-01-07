package network.labs.lab1.client;

import network.labs.lab1.client.commands.*;
import network.labs.lab1.common.*;
import network.labs.lab2.util.PathsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Scanner;

/**
 * TCP-клиент для ЛР №1.
 * Поддерживает keepalive и таймауты для обнаружения обрывов.
 */
public class TcpClient {
    private static final Logger log = LoggerFactory.getLogger(TcpClient.class);
    private final String host;
    private final int port;
    private final Path clientDir;

    public TcpClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.clientDir = PathsConfig.CLIENT_TCP;
        FileUtils.ensureDirectory(clientDir);
    }

    public void start() {
        try (Socket socket = new Socket(host, port)) {
            // Настройка keepalive для обнаружения обрывов
            socket.setKeepAlive(true);
            socket.setSoTimeout(120_000); // 2 минуты на ответ

            TcpClientCommandContext ctx = new TcpClientCommandContext(socket, clientDir);
            log.info("Подключено к TCP {}:{}", host, port);

            CommandRegistry<CommandContext> textRegistry = createTextRegistry();
            CommandRegistry<FileAwareContext> fileRegistry = createFileRegistry();

            Scanner scanner = new Scanner(System.in, "UTF-8");
            boolean running = true;

            while (running) {
                System.out.print("> ");
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                String cmd = line.split("\\s+")[0].toUpperCase();

                try {
                    if ("UPLOAD".equals(cmd) || "DOWNLOAD".equals(cmd)) {
                        CommandResult res = fileRegistry.dispatch(line, ctx);
                        if (res == CommandResult.CLOSE) running = false;
                    } else {
                        CommandResult res = textRegistry.dispatch(line, ctx);
                        if (res == CommandResult.CLOSE) running = false;
                    }
                } catch (IOException e) {
                    log.error("Ошибка связи", e);
                    System.err.println("Соединение разорвано: " + e.getMessage());
                    running = false;
                }
            }
        } catch (IOException e) {
            log.error("Не удалось подключиться", e);
            System.err.println("Ошибка подключения: " + e.getMessage());
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
        reg.register(new UploadCommand());
        reg.register(new DownloadCommand());
        return reg;
    }
}