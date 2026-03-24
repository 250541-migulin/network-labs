package network.labs.lab1.server;

import network.labs.lab1.common.*;
import network.labs.lab1.server.commands.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

/**
 * Последовательный TCP-сервер (ЛР №1) с поддержкой докачки.
 * Обрабатывает одного клиента за раз.
 */
public class TcpServer {
    private static final Logger log = LoggerFactory.getLogger(TcpServer.class);

    // Настройки
    private static final int CLIENT_SOCKET_TIMEOUT_MS = 120_000; // 2 минуты
    private static final int SERVER_BACKLOG = 1; // Минимальная очередь (последовательный сервер)

    // Состояние для восстановления докачки
    private InetAddress lastClientIp = null;
    private String lastFilename = null;

    // Регистры команд (создаём один раз)
    private final CommandRegistry<CommandContext> textRegistry;
    private final CommandRegistry<FileAwareContext> fileRegistry; // команды для работы с файлами

    public TcpServer() {

        // Инициализация регистров команд
        this.textRegistry = createTextRegistry();
        this.fileRegistry = createFileRegistry();

        log.info("Сервер инициализирован, директории подготовлены");
    }

    /**
     * Главный цикл сервера: принимает клиентов последовательно.
     */
    public void start() {
        // try-with-resources для ServerSocket (AutoCloseable)
        try (ServerSocket serverSocket = new ServerSocket(Config.TCP_SERVER_PORT, SERVER_BACKLOG)) {

            log.info("🚀 Сервер запущен на порту {}", Config.TCP_SERVER_PORT);

            // Бесконечный цикл приёма клиентов (последовательная обработка)
            while (true) {
                acceptAndHandleClient(serverSocket);
            }

        } catch (IOException e) {
            log.error("Сервер остановлен", e);
        }
    }

    /**
     * Принимает одного клиента и обрабатывает его.
     */
    private void acceptAndHandleClient(ServerSocket serverSocket) {
        try {
            Socket client = serverSocket.accept();
            InetAddress clientIp = client.getInetAddress();

            log.info("Подключился клиент: {} (IP: {})",
                    client.getRemoteSocketAddress(), clientIp);

            // Настройки сокета клиента
            client.setKeepAlive(true);
            client.setSoTimeout(CLIENT_SOCKET_TIMEOUT_MS);

            // Обработка клиента
            handleClient(client, clientIp);

            log.info("Клиент отключился");

        } catch (SocketException e) {
            log.info("Клиент разорвал соединение: {}", e.getMessage());
        } catch (IOException e) {
            // Другие сетевые ошибки
            log.error("Ошибка при работе с клиентом", e);
        }
    }

    /**
     * Обрабатывает команды клиента в цикле.
     */
    private void handleClient(Socket client, InetAddress clientIp) {
        // try-with-resources для клиентского сокета
        try (client) {
            TcpServerCommandContext ctx = new TcpServerCommandContext(client, Config.SOURCE_DIR, clientIp);

            while (true) {
                String line = ctx.readLine();

                // null = клиент закрыл соединение
                if (line == null) {
                    log.debug("🔌 Клиент закрыл соединение");
                    break;
                }

                // Парсим команду
                String cmd = line.split("\\s+")[0].toUpperCase();

                // Диспетчеризация: файловые команды или текстовые
                CommandResult res = isFileCommand(cmd)
                        ? fileRegistry.dispatch(line, ctx)
                        : textRegistry.dispatch(line, ctx);

                // Команда CLOSE завершает сессию
                if (res == CommandResult.CLOSE) {
                    log.debug("👋 Клиент запросил завершение");
                    break;
                }
            }

        } catch (SocketException e) {
            log.info("⚠️ Клиент разорвал соединение: {}", e.getMessage());
        } catch (IOException e) {
            log.error("❌ Ошибка обработки клиента", e);
        }
    }

    /**
     * Проверяет, является ли команда файловой.
     */
    private boolean isFileCommand(String cmd) {
        return "UPLOAD".equals(cmd) || "DOWNLOAD".equals(cmd);
    }

    // --- Методы для докачки ---

    public boolean isSameClientAndFile(InetAddress ip, String filename) {
        return lastClientIp != null && lastClientIp.equals(ip)
                && lastFilename != null && lastFilename.equals(filename);
    }

    public void setLastSession(InetAddress ip, String filename) {
        this.lastClientIp = ip;
        this.lastFilename = filename;
        log.debug("💾 Сессия сохранена: {} → {}", ip, filename);
    }

    public void clearSession() {
        this.lastClientIp = null;
        this.lastFilename = null;
        log.debug("🗑️ Сессия очищена");
    }

    // --- Создание регистров команд ---

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
        reg.register(new UploadCommand(Config.SOURCE_DIR, this));
        reg.register(new DownloadCommand(Config.TMP_DIR, this));
        return reg;
    }
}