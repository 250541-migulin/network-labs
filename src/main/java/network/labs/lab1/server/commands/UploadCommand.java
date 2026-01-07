package network.labs.lab1.server.commands;

import network.labs.lab1.common.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;

public class UploadCommand implements Command<FileAwareContext> {
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
    public CommandResult execute(String[] args, FileAwareContext ctx) throws IOException {
        if (args.length < 1) {
            ctx.writeLine("ОШИБКА: имя файла не указано");
            return CommandResult.ERROR;
        }
        String filename = args[0];
        Path target = serverDir.resolve(filename);

        ctx.writeLine("ОК");
        log.info("Готов принимать файл {}", filename);

        String sizeLine = ctx.readLine();
        long size = Long.parseLong(sizeLine.trim());
        log.info("Ожидаем {} байт", size);

        long start = System.currentTimeMillis();
        try (OutputStream fos = Files.newOutputStream(target)) {
            long received = IoUtils.copyStream(ctx.inputStream(), fos, size);
            long elapsed = System.currentTimeMillis() - start;

            String rate = IoUtils.formatTransferRate(received, elapsed);
            log.info("Файл '{}' загружен: {}", filename, rate);
            ctx.writeLine("Файл '" + filename + "' загружен: " + rate);
        }
        return CommandResult.CONTINUE;
    }
}