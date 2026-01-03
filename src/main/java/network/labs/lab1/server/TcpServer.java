package network.labs.lab1.server;

import network.labs.lab1.common.*;
import network.labs.lab1.server.commands.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Path;

/**
 * TCP-сервер для обмена файлами.
 * <p>
 * Сервер является последовательным: обслуживает только одного клиента
 * в данный момент. Новые подключения во время работы отвергаются.
 */
public class TcpServer {
    private static final Logger log = LoggerFactory.getLogger(TcpServer.class);

    private final int port;
    private final Path serverDir;
    private final CommandRegistry<ServerCommandContext> registry;
    private boolean busy = false;

    /**
     * Создаёт сервер на указанном порту с дефолтной директорией хранения файлов.
     *
     * @param port порт сервера
     */
    public TcpServer(int port) {
        this.port = port;
        this.serverDir = Path.of("server_files");
        FileUtils.ensureDirectory(serverDir);
        this.registry = createRegistry();
    }

    /**
     * Запускает сервер: принимает клиентов и обрабатывает их команды.
     * Сервер обслуживает только одного клиента одновременно.
     */
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log.info("Сервер запущен на порту {}", port);

            while (true) {
                Socket clientSocket = serverSocket.accept();

                if (busy) {
                    log.warn("Отклонено подключение: {}", clientSocket.getRemoteSocketAddress());
                    clientSocket.close();
                    continue;
                }

                busy = true;
                log.info("Подключился клиент: {}", clientSocket.getRemoteSocketAddress());

                handleClient(clientSocket);

                busy = false;
                log.info("Клиент отключился");
            }
        } catch (IOException e) {
            log.error("Ошибка запуска сервера", e);
        }
    }

    /**
     * Обрабатывает команды подключённого клиента.
     *
     * @param socket сокет клиента
     */
    private void handleClient(Socket socket) {
        try (socket) {
            ServerCommandContext ctx = new ServerCommandContext(socket, socket.getInputStream(), socket.getOutputStream());

            boolean running = true;
            while (running) {
                String line = IoUtils.readLine(ctx.in());
                if (line == null) break; // клиент закрыл соединение

                CommandResult result = registry.dispatch(line, ctx);
                if (result == CommandResult.ERROR) {
                    ctx.writeLine("ОШИБКА: неизвестная команда");
                    log.warn("Неизвестная команда от клиента: {}", line);
                }
                if (result == CommandResult.CLOSE) {
                    running = false;
                }
            }

        } catch (SocketException e) {
            log.info("Клиент разорвал соединение: {}", e.getMessage());
        } catch (IOException e) {
            log.error("Ошибка обработки клиента", e);
        }
    }

    /**
     * Создаёт и регистрирует команды сервера.
     *
     * @return реестр команд
     */
    private CommandRegistry<ServerCommandContext> createRegistry() {
        CommandRegistry<ServerCommandContext> registry = new CommandRegistry<>();
        registry.register(new EchoCommand());
        registry.register(new TimeCommand());
        registry.register(new UploadCommand(serverDir));
        registry.register(new DownloadCommand(serverDir));
        registry.register(new CloseCommand());
        registry.register(new UnknownCommand());
        return registry;
    }
}
