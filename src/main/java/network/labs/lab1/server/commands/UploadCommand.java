package network.labs.lab1.server.commands;

import network.labs.lab1.common.*;
import network.labs.lab1.server.ServerCommandContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;

/**
 * Команда UPLOAD: принимает файл от клиента и сохраняет его на сервере.
 */
public class UploadCommand implements Command<ServerCommandContext> {
    private static final Logger log = LoggerFactory.getLogger(UploadCommand.class);

    private final Path serverDir;

    public UploadCommand(Path serverDir) {
        this.serverDir = serverDir;
    }

    @Override
    public String name() {
        return CommandName.UPLOAD.key();
    }

    @Override
    public CommandResult execute(String[] args, ServerCommandContext ctx) throws IOException {
        if (args.length < 1) {
            ctx.writeLine("ОШИБКА: имя файла не указано");
            return CommandResult.ERROR;
        }
        String filename = args[0];
        Path target = serverDir.resolve(filename);

        // подтверждаем клиенту готовность
        ctx.writeLine("ОК");
        log.info("Готов принимать файл {}", filename);

        // читаем размер файла
        String sizeLine = IoUtils.readLine(ctx.in());
        long size = Long.parseLong(sizeLine.trim());
        log.info("Ожидаем {} байт", size);

        // принимаем файл с замером времени
        long start = System.currentTimeMillis();
        try (OutputStream fos = Files.newOutputStream(target)) {
            long received = IoUtils.copyStream(ctx.in(), fos, size);
            long elapsed = System.currentTimeMillis() - start;

            String rate = IoUtils.formatTransferRate(received, elapsed);
            log.info("Файл '{}' загружен: {}", filename, rate);
            ctx.writeLine("Файл '" + filename + "' загружен: " + rate);
        }

        return CommandResult.CONTINUE;
    }
}
