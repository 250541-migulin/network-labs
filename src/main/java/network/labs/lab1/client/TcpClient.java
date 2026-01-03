package network.labs.lab1.client;

import network.labs.lab1.client.commands.*;
import network.labs.lab1.common.*;
import network.labs.lab1.server.ServerCommandContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Scanner;

/**
 * TCP‑клиент для обмена файлами.
 * <p>
 * Подключается к серверу, принимает команды пользователя и обрабатывает ответы.
 */
public class TcpClient {
    private static final Logger log = LoggerFactory.getLogger(TcpClient.class);

    private final String host;
    private final int port;
    private final Path clientDir;
    private final CommandRegistry<ClientCommandContext> registry;

    /**
     * @param host адрес сервера
     * @param port порт сервера
     */
    public TcpClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.clientDir = Path.of("client_files");
        this.registry = createRegistry();

        FileUtils.ensureDirectory(clientDir);
    }

    /**
     * Запускает цикл работы клиента: подключение, ввод команд, обработка ответов.
     */
    public void start() {
        try (Socket socket = new Socket(host, port)) {
            ClientCommandContext ctx = new ClientCommandContext(
                    socket.getInputStream(),
                    socket.getOutputStream(),
                    clientDir
            );


            log.info("Подключено к {}:{}", host, port);

            Scanner scanner = new Scanner(System.in, "UTF-8");
            boolean running = true;
            while (running) {
                System.out.print("> "); // оставляем интерактивный prompt в консоли
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                CommandResult result = registry.dispatch(line, ctx);
                if (result == CommandResult.CLOSE) {
                    running = false;
                }
            }
        } catch (IOException e) {
            log.error("Ошибка подключения к {}:{}", host, port, e);
        }
    }

    /**
     * Создаёт и регистрирует команды клиента.
     *
     * @return реестр команд
     */
    private CommandRegistry<ClientCommandContext> createRegistry() {
        CommandRegistry<ClientCommandContext> registry = new CommandRegistry<>();
        registry.register(new EchoCommand());
        registry.register(new TimeCommand());
        registry.register(new UploadCommand());
        registry.register(new DownloadCommand());
        registry.register(new CloseCommand());
        registry.register(new UnknownCommand());
        return registry;
    }
}
