// TcpClient.java
package network.labs.lab1.client;

import network.labs.lab1.client.commands.*;
import network.labs.lab1.common.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Scanner;

/**
 * TCP-клиент для ЛР №1.
 * Поддерживает keepalive и таймауты для обнаружения обрывов.
 */
public class TcpClient {
    private static final Logger log = LoggerFactory.getLogger(TcpClient.class);

    private final String host;
    private final int port;
    private final int timeout;

    public TcpClient(String host, int port, int timeout) {
        this.host = host;
        this.port = port;
        this.timeout = timeout;

        log.info("✅ Клиент инициализирован, директории подготовлены");
    }

    /**
     * Запускает клиент: подключается к серверу и обрабатывает команды.
     * Блокирующий метод — работает пока соединение активно.
     * @throws IOException при сетевых ошибках (пробрасывается в main)
     */
    public void start() throws IOException {
        // try-with-resources для сокета — гарантированное закрытие
            Socket socket = new Socket(host, port);

            socket.setKeepAlive(true);
            socket.setSoTimeout(timeout);

            log.info("🔗 Подключено к {}:{}", host, port);

            // Контекст и регистры команд
            TcpClientCommandContext ctx = new TcpClientCommandContext(socket, Config.SOURCE_DIR);
            CommandRegistry<CommandContext> textRegistry = createTextRegistry();
            CommandRegistry<FileAwareContext> fileRegistry = createFileRegistry();

            // Главный цикл: чтение команд
            Scanner scanner = new Scanner(System.in, "UTF-8");
            while (true) {
                System.out.print("> ");
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                // Диспетчеризация команд
                CommandResult res = dispatchCommand(line, ctx, textRegistry, fileRegistry);
                if (res == CommandResult.CLOSE) {
                    log.info("Завершение работы по команде пользователя");
                    break;
                }
            }

    }

    /**
     * Диспетчеризация: определяет тип команды и вызывает нужный регистр.
     */
    private CommandResult dispatchCommand(String line, TcpClientCommandContext ctx,
                                          CommandRegistry<CommandContext> textReg,
                                          CommandRegistry<FileAwareContext> fileReg) throws IOException {
        String cmd = line.split("\\s+")[0].toUpperCase();

        if (isFileCommand(cmd)) {
            return fileReg.dispatch(line, ctx);
        } else {
            return textReg.dispatch(line, ctx);
        }
    }

    /**
     * Проверяет, является ли команда файловой.
     */
    private boolean isFileCommand(String cmd) {
        return "UPLOAD".equals(cmd) || "DOWNLOAD".equals(cmd);
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
        // Симметрично серверу: клиент отправляет из SOURCE, получает в TMP
        reg.register(new UploadCommand());
        reg.register(new DownloadCommand());
        return reg;
    }
}