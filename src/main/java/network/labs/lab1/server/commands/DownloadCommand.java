package network.labs.lab1.server.commands;

import network.labs.lab1.common.*;
import network.labs.lab1.server.TcpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;

public class DownloadCommand implements Command<FileAwareContext> {
    private static final Logger log = LoggerFactory.getLogger(DownloadCommand.class);
    private final Path serverDir;
    private final TcpServer server;

    public DownloadCommand(Path serverDir, TcpServer server) {
        this.serverDir = serverDir;
        this.server = server;
    }

    @Override
    public String name() {
        return CommandName.DOWNLOAD.key();
    }

    @Override
    public CommandResult execute(String[] args, FileAwareContext ctx) throws IOException {
        // --- 1. Валидация ---
        if (args.length < 1) {
            ctx.writeLine("ОШИБКА: имя файла не указано");
            return CommandResult.ERROR;
        }

        String filename = args[0];
        long offset = 0;

        if (args.length >= 2) {
            try {
                offset = Long.parseLong(args[1]);
            } catch (NumberFormatException e) {
                ctx.writeLine("ОШИБКА: некорректный offset");
                return CommandResult.ERROR;
            }
        }

        Path source = serverDir.resolve(filename).normalize();
        if (!source.startsWith(serverDir)) {
            ctx.writeLine("ОШИБКА: недопустимый путь");
            return CommandResult.ERROR;
        }

        if (!Files.exists(source)) {
            ctx.writeLine("ОШИБКА: файл не найден — " + filename);
            return CommandResult.ERROR;
        }

        long fileSize = Files.size(source);
        if (offset > fileSize) {
            ctx.writeLine("ОШИБКА: offset больше размера файла");
            return CommandResult.ERROR;
        }

        long remaining = fileSize - offset;
        ctx.writeLine("ОК");
        ctx.writeLine(String.valueOf(remaining));

        if (remaining == 0) {
            ctx.writeLine("Файл '" + filename + "' уже полностью скачан");
            server.setLastSession(null, null);
            return CommandResult.CONTINUE;
        }

        // --- 2. Отключаем SO_TIMEOUT ---
        Socket socket = ctx.getSocket();
        int originalTimeout = socket.getSoTimeout();
        socket.setSoTimeout(0);

        // --- 3. Передача ---
        try {
            try (InputStream fis = Files.newInputStream(source)) {
                fis.skipNBytes(offset);
                IoUtils.copyStream(fis, ctx.outputStream(), remaining);
            }

            String rate = IoUtils.formatTransferRate(remaining, 1);
            log.info("Файл '{}' отправлен ({} байт)", filename, remaining);
            ctx.writeLine("Файл '" + filename + "' отправлен: " + rate);

            server.setLastSession(null, null);
            return CommandResult.CONTINUE;

        } catch (IOException e) {
            // сохраняем сессию — без сообщения клиенту
            log.info("Потеряно соединение при DOWNLOAD '{}'. Ожидание восстановления...", filename);
            server.setLastSession(ctx.getClientIp(), filename);
            return CommandResult.CLOSE;
        } finally {
            socket.setSoTimeout(originalTimeout);
        }
    }
}