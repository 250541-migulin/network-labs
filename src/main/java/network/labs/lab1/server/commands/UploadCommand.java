package network.labs.lab1.server.commands;

import network.labs.lab1.common.*;
import network.labs.lab1.server.TcpServer;
import network.labs.lab1.server.TcpServerCommandContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.*;

public class UploadCommand implements Command<FileAwareContext> {
    private static final Logger log = LoggerFactory.getLogger(UploadCommand.class);
    private final Path serverDir;
    private final TcpServer server;

    public UploadCommand(Path serverDir, TcpServer server) {
        this.serverDir = serverDir;
        this.server = server;
    }

    @Override
    public String name() {
        return CommandName.UPLOAD.key();
    }

    @Override
    public CommandResult execute(String[] args, FileAwareContext ctx) throws IOException {
        // --- 1. Валидация аргументов ---
        if (args.length < 1) {
            ctx.writeLine("ОШИБКА: имя файла не указано");
            return CommandResult.ERROR;
        }

        String filename = args[0];
        Path target = serverDir.resolve(filename).normalize();
        if (!target.startsWith(serverDir)) {
            ctx.writeLine("ОШИБКА: недопустимый путь");
            return CommandResult.ERROR;
        }

        TcpServerCommandContext srvCtx = (TcpServerCommandContext) ctx;
        InetAddress clientIp = srvCtx.getClientIp();

        // --- 2. Подготовка файла ---
        boolean isNewFile = !Files.exists(target);
        long offset = 0;

        if (!isNewFile) {
            if (server.isSameClientAndFile(clientIp, filename)) {
                offset = Files.size(target);
                log.info("Возобновляем загрузку файла '{}' (уже есть {} байт)", filename, offset);
            } else {
                try {
                    Files.delete(target);
                    isNewFile = true;
                    log.info("Удалён старый файл '{}' (другой клиент)", filename);
                } catch (IOException e) {
                    log.warn("Не удалось удалить файл {}", filename, e);
                    ctx.writeLine("ОШИБКА: не удалось очистить файл");
                    return CommandResult.ERROR;
                }
            }
        }

        // --- 3. Отправка OK + offset ---
        ctx.writeLine("OK " + offset);

        // --- 4. Получение размера ---
        String sizeLine = ctx.readLine();
        if (sizeLine == null) {
            ctx.writeLine("ОШИБКА: не получен размер");
            return CommandResult.ERROR;
        }

        long remaining;
        try {
            remaining = Long.parseLong(sizeLine.trim());
        } catch (NumberFormatException e) {
            ctx.writeLine("ОШИБКА: некорректный размер");
            return CommandResult.ERROR;
        }

        if (remaining < 0) {
            ctx.writeLine("ОШИБКА: отрицательный размер");
            return CommandResult.ERROR;
        }

        // --- 5. Настройка сокета: отключаем SO_TIMEOUT ---
        Socket socket = ctx.getSocket();
        int originalTimeout = socket.getSoTimeout();
        socket.setSoTimeout(0); // отключаем таймаут на время передачи

        // --- 6. Передача данных ---
        try {
            try (OutputStream fos = Files.newOutputStream(target,
                    StandardOpenOption.CREATE,
                    offset > 0 ? StandardOpenOption.APPEND : StandardOpenOption.WRITE)) {
                IoUtils.copyStream(ctx.inputStream(), fos, remaining);
            }

            long totalSize = Files.size(target);
            String rate = IoUtils.formatTransferRate(totalSize, 1);
            log.info("Файл '{}' загружен ({} байт)", filename, totalSize);
            ctx.writeLine("Файл '" + filename + "' загружен: " + rate);

            server.setLastSession(null, null); // сброс сессии
            return CommandResult.CONTINUE;

        } catch (IOException e) {
            // Сетевой разрыв — НЕ отправляем ошибку клиенту!
            log.info("Потеряно соединение при UPLOAD '{}'. Ожидание восстановления...", filename);
            server.setLastSession(clientIp, filename); // сохраняем для докачки
            return CommandResult.CLOSE;
        } finally {
            // Восстанавливаем исходный таймаут
            socket.setSoTimeout(originalTimeout);
        }
    }
}