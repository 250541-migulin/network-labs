package network.labs.lab1.server.commands;

import network.labs.lab1.common.*;
import network.labs.lab1.server.TcpServer;
import network.labs.lab1.server.TcpServerCommandContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
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
        if (args.length < 1) {
            ctx.writeLine("ОШИБКА: имя файла не указано");
            return CommandResult.ERROR;
        }

        String filename = args[0];
        Path target = serverDir.resolve(filename);

        TcpServerCommandContext srvCtx = (TcpServerCommandContext) ctx;
        InetAddress clientIp = srvCtx.getClientIp();

        boolean resume = false;
        if (Files.exists(target)) {
            if (server.isSameClientAndFile(clientIp, filename)) {
                resume = true;
                log.info("Возобновляем загрузку файла '{}' (уже есть {} байт)", filename, Files.size(target));
            } else {
                // Другой клиент — удаляем старый файл
                Files.delete(target);
                log.info("Удалён старый файл '{}' (другой клиент)", filename);
            }
        }

        ctx.writeLine("ОК");
        String sizeLine = ctx.readLine();
        long expectedSize = Long.parseLong(sizeLine.trim());
        long existingSize = resume ? Files.size(target) : 0;
        long toReceive = expectedSize - existingSize;

        if (toReceive < 0) {
            ctx.writeLine("ОШИБКА: размер файла на клиенте меньше, чем на сервере");
            return CommandResult.ERROR;
        }

        try (OutputStream fos = Files.newOutputStream(target,
                StandardOpenOption.CREATE,
                resume ? StandardOpenOption.APPEND : StandardOpenOption.WRITE)) {
            IoUtils.copyStream(ctx.inputStream(), fos, toReceive);
        }

        long finalSize = Files.size(target);
        String rate = IoUtils.formatTransferRate(toReceive, 1); // точное время не критично
        log.info("Файл '{}' загружен (итоговый размер: {} байт)", filename, finalSize);
        ctx.writeLine("Файл '" + filename + "' загружен: " + rate);

        server.setLastSession(clientIp, filename);
        return CommandResult.CONTINUE;
    }
}