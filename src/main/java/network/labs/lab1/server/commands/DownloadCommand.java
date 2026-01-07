package network.labs.lab1.server.commands;

import network.labs.lab1.common.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;

public class DownloadCommand implements Command<FileAwareContext> {
    private static final Logger log = LoggerFactory.getLogger(DownloadCommand.class);
    private final Path serverDir;

    public DownloadCommand(Path serverDir) {
        this.serverDir = serverDir;
    }

    @Override
    public String name() {
        return CommandName.DOWNLOAD.key();
    }

    @Override
    public CommandResult execute(String[] args, FileAwareContext ctx) throws IOException {
        if (args.length < 1) {
            ctx.writeLine("ОШИБКА: имя файла не указано");
            return CommandResult.ERROR;
        }
        String filename = args[0];
        Path source = serverDir.resolve(filename);

        if (!Files.exists(source)) {
            ctx.writeLine("ОШИБКА: файл не найден — " + filename);
            return CommandResult.ERROR;
        }

        long size = Files.size(source);
        ctx.writeLine("ОК");
        ctx.writeLine(String.valueOf(size));
        log.info("Отправляю файл '{}' ({} байт)", filename, size);

        long start = System.currentTimeMillis();
        try (InputStream fis = Files.newInputStream(source)) {
            IoUtils.copyStream(fis, ctx.outputStream(), size);
        }
        long elapsed = System.currentTimeMillis() - start;

        String rate = IoUtils.formatTransferRate(size, elapsed);
        log.info("Файл '{}' отправлен: {}", filename, rate);
        ctx.writeLine("Файл '" + filename + "' отправлен: " + rate);
        return CommandResult.CONTINUE;
    }
}