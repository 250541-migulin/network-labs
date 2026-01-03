package network.labs.lab1.server;

import network.labs.lab1.common.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
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

    /**
     * Конструктор сервера.
     *
     * @param port порт, на котором будет слушать сервер
     */
    public TcpServer(int port) {
        this.port = port;
        this.serverDir = Path.of("server_files");
        this.commandRegistry = new HashMap<>();
        initServerDirectory();
        registerCommands();
    }

    /**
     * Создаёт директорию для файлов сервера.
     */
    private void initServerDirectory() {
        try {
            Files.createDirectories(serverDir);
            log.info("Директория сервера готова: {}", serverDir.toAbsolutePath());
        } catch (IOException e) {
            log.error("Не удалось создать директорию сервера", e);
            throw new IllegalStateException("Директория сервера недоступна", e);
        }
    }

    /**
     * Запускает сервер и принимает подключения клиентов.
     */
    public void start() {
        log.info("Запуск сервера на порту {}", port);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log.info("Сервер слушает порт {}", port);

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setKeepAlive(true);
                    handleClient(clientSocket);
                } catch (IOException e) {
                    log.warn("Ошибка при принятии подключения клиента", e);
                }
            }
        } catch (IOException e) {
            log.error("Ошибка серверного сокета", e);
        }
    }

    /**
     * Обрабатывает подключение клиента.
     *
     * @param clientSocket сокет клиента
     */
    private void handleClient(Socket clientSocket) {
        String clientAddr = clientSocket.getRemoteSocketAddress().toString();
        log.info("Клиент подключился: {}", clientAddr);

        try (clientSocket) {
            CommandContext ctx = new CommandContext(clientSocket);
            processClientCommands(ctx, clientAddr);
        } catch (IOException e) {
            log.warn("Соединение с клиентом прервано: {}", clientAddr, e);
        } finally {
            log.info("Клиент отключился: {}", clientAddr);
        }
    }

    /**
     * Обрабатывает команды клиента в цикле.
     *
     * @param ctx        контекст команды (сокет, потоки)
     * @param clientAddr адрес клиента
     * @throws IOException при ошибках ввода-вывода
     */
    private void processClientCommands(CommandContext ctx, String clientAddr) throws IOException {
        String commandLine;
        while ((commandLine = IoUtils.readLine(ctx.in())) != null) {
            log.debug("[{}] Получено: {}", clientAddr, commandLine);
            if (executeCommand(commandLine, ctx)) {
                break;
            }
        }
    }

    /**
     * Выполняет команду клиента.
     *
     * @param line строка команды
     * @param ctx  контекст команды
     * @return true, если нужно закрыть соединение
     */
    private boolean executeCommand(String line, CommandContext ctx) {
        String[] parts = line.trim().split("\\s+", 2);
        if (parts.length == 0) {
            return false;
        }

        String commandName = parts[0].toUpperCase();
        String[] args = parts.length > 1 ? new String[]{parts[1].trim()} : new String[0];

        CommandSpec spec = commandRegistry.get(commandName);
        if (spec == null) {
            sendResponse(ctx, "ОШИБКА: неизвестная команда " + commandName);
            return false;
        }

        if (!spec.validate(args)) {
            sendResponse(ctx, String.format(
                    "ОШИБКА: команда %s требует от %d до %d аргументов",
                    commandName, spec.minArgs(), spec.maxArgs()
            ));
            return false;
        }

        return spec.handler().handle(args, ctx);
    }

    /**
     * Отправляет строковый ответ клиенту.
     *
     * @param ctx     контекст команды
     * @param message сообщение для отправки
     */
    private void sendResponse(CommandContext ctx, String message) {
        try {
            IoUtils.writeLine(ctx.out(), message);
        } catch (IOException e) {
            log.warn("Не удалось отправить ответ клиенту", e);
        }
    }

    /**
     * Регистрирует доступные команды сервера.
     */
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
            sendResponse(ctx, "Соединение закрыто");
            return true;
        };
        commandRegistry.put("CLOSE", new CommandSpec(0, 0, closeHandler));
        commandRegistry.put("QUIT", new CommandSpec(0, 0, closeHandler));
        commandRegistry.put("EXIT", new CommandSpec(0, 0, closeHandler));

        commandRegistry.put("UPLOAD", new CommandSpec(1, 1, this::handleUpload));
        commandRegistry.put("DOWNLOAD", new CommandSpec(1, 1, this::handleDownload));
    }

    /**
     * Обрабатывает команду загрузки файла от клиента.
     *
     * @param args аргументы команды (имя файла)
     * @param ctx  контекст команды
     * @return false (соединение не закрывается)
     */
    private boolean handleUpload(String[] args, CommandContext ctx) {
        String filename = args[0];
        String clientAddr = ctx.socket().getRemoteSocketAddress().toString();
        Path target = serverDir.resolve(filename);
        Path partFile = serverDir.resolve(filename + ".part");
        Path resumeInfo = serverDir.resolve(filename + ".resume");

        try {
            // 1. Готовим состояние возобновления (resume) и определяем смещение
            UploadResumeState state = prepareUploadResume(partFile, resumeInfo, clientAddr);

            // 2. Сообщаем клиенту режим (НАЧАТЬ/ПРОДОЛЖИТЬ) и смещение
            sendResponse(ctx, (state.isResume() ? "ПРОДОЛЖИТЬ " : "НАЧАТЬ ") + state.offset());

            // 3. Читаем ожидаемый размер оставшейся части файла
            String sizeLine = IoUtils.readLine(ctx.in());
            long remaining = Long.parseLong(sizeLine.trim());
            log.debug("[{}] Ожидаемый размер файла: {} байт", clientAddr, remaining);

            // 4. Принимаем байты файла в .part (с учётом resume)
            long start = System.nanoTime();
            long received = receiveFileContent(ctx, partFile, state.isResume(), remaining);
            long end = System.nanoTime();

            // 5. Финализируем загрузку: переносим .part в целевой файл и очищаем служебные данные
            finalizeUpload(partFile, target, resumeInfo, filename);

            // 6. Отправляем финальное сообщение (битрейт) и логируем результат
            sendResponse(ctx, String.format(
                    "Файл '%s' загружен (%d байт, %s КБ/с)",
                    filename, received, formatBitrate(received, end - start)
            ));
            logTransfer("Загрузка", filename, clientAddr, received, end - start, true);

        } catch (IOException e) {
            // 7. Обрабатываем ошибки ввода-вывода
            log.error("Ошибка загрузки файла: {}", filename, e);
            sendResponse(ctx, "ОШИБКА: загрузка не удалась: " + e.getMessage());
        }
        // 8. Соединение остаётся открытым (возвращаем false)
        return false;
    }

    /**
     * Подготавливает состояние для возобновления загрузки.
     *
     * @param partFile   временный файл
     * @param resumeInfo файл с информацией о клиенте
     * @param clientAddr адрес клиента
     * @return состояние загрузки (resume/offset)
     * @throws IOException при ошибках ввода-вывода
     */
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

    /**
     * Принимает содержимое файла от клиента.
     *
     * @param ctx      контекст команды
     * @param partFile временный файл
     * @param resume   true, если продолжаем загрузку
     * @param length   ожидаемый размер (байты)
     * @return количество принятых байт
     * @throws IOException при ошибках ввода-вывода
     */
    private long receiveFileContent(CommandContext ctx, Path partFile, boolean resume, long length) throws IOException {
        try (OutputStream out = Files.newOutputStream(partFile,
                StandardOpenOption.CREATE,
                resume ? StandardOpenOption.APPEND : StandardOpenOption.WRITE)) {
            return IoUtils.copyStream(ctx.in(), out, length);
        }
    }

    /**
     * Финализирует загрузку, перемещая временный файл на целевой путь.
     *
     * @param partFile   временный файл
     * @param target     целевой файл
     * @param resumeInfo файл с информацией о клиенте
     * @param filename   имя файла
     * @throws IOException при ошибках файловых операций
     */
    private void finalizeUpload(Path partFile, Path target, Path resumeInfo, String filename) throws IOException {
        Files.move(partFile, target, StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(resumeInfo);
    }

    /**
     * Обрабатывает команду скачивания файла клиентом.
     *
     * @param args аргументы команды (имя файла)
     * @param ctx  контекст команды
     * @return true, если скачивание завершено полностью
     */
    private boolean handleDownload(String[] args, CommandContext ctx) {
        String filename = args[0];
        Path source = serverDir.resolve(filename);
        String clientAddr = ctx.socket().getRemoteSocketAddress().toString();

        // 1. Проверяем наличие файла на сервере
        if (!Files.exists(source)) {
            sendResponse(ctx, "ОШИБКА: файл не найден: " + filename);
            return false;
        }

        try {
            // 2. Отправляем статус "ОК" и ожидаемый размер файла
            long expectedSize = Files.size(source);
            sendResponse(ctx, "ОК");
            IoUtils.writeLine(ctx.out(), String.valueOf(expectedSize));

            // 3. Передаём байты файла
            long start = System.nanoTime();
            long sent;
            try (InputStream fis = Files.newInputStream(source)) {
                sent = IoUtils.copyStream(fis, ctx.out(), expectedSize);
            }
            long end = System.nanoTime();

            // 4. Формируем итог: успешно или частично
            boolean success = (sent == expectedSize);
            if (success) {
                sendResponse(ctx, String.format(
                        "Файл '%s' отправлен (%d байт, %s КБ/с)",
                        filename, sent, formatBitrate(sent, end - start)
                ));
            } else {
                sendResponse(ctx, String.format(
                        "ПРЕДУПРЕЖДЕНИЕ: отправлено %d из %d байт",
                        sent, expectedSize
                ));
            }

            // 5. Логируем результат передачи
            logTransfer("Скачивание", filename, clientAddr, sent, end - start, success);

            // 6. Возвращаем флаг полного завершения (true/false)
            return success;

        } catch (IOException e) {
            // 7. Обрабатываем ошибки ввода-вывода
            log.error("Ошибка скачивания файла: {}", filename, e);
            sendResponse(ctx, "ОШИБКА: скачивание не удалось: " + e.getMessage());
            return false;
        }
    }

    /**
     * Форматирует скорость передачи в КБ/с с точностью до двух знаков.
     *
     * @param bytes количество байт
     * @param nanos длительность операции в наносекундах
     * @return строка со скоростью в КБ/с
     */
    private String formatBitrate(long bytes, long nanos) {
        double seconds = nanos / 1_000_000_000.0;
        double kbps = (bytes / 1024.0) / Math.max(seconds, 1e-9);
        return String.format("%.2f", kbps);
    }

    /**
     * Логирует результат передачи (загрузка/скачивание) в единообразном формате.
     *
     * @param action     действие: "Загрузка" или "Скачивание"
     * @param filename   имя файла
     * @param clientAddr адрес клиента
     * @param bytes      количество байт
     * @param nanos      длительность операции в наносекундах
     * @param success    true, если операция завершена полностью
     */
    private void logTransfer(String action, String filename, String clientAddr,
                             long bytes, long nanos, boolean success) {
        String kbps = formatBitrate(bytes, nanos);
        String arrow = "Загрузка".equals(action) ? "←" : "→";
        if (success) {
            log.info("{} завершена: {} {} {} ({} байт, {} КБ/с)",
                    action, filename, arrow, clientAddr, bytes, kbps);
        } else {
            log.warn("{} частично: {} {} {} ({} байт, {} КБ/с)",
                    action, filename, arrow, clientAddr, bytes, kbps);
        }
    }

    /**
     * Состояние возобновления загрузки.
     *
     * @param isResume true, если продолжаем прерванную загрузку
     * @param offset   текущий размер временного файла (байты)
     */
    private record UploadResumeState(boolean isResume, long offset) {}
}
