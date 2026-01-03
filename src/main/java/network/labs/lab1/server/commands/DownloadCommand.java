package network.labs.lab1.server.commands;

import network.labs.lab1.common.*;
import network.labs.lab1.server.ServerCommandContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;

/**
 * Команда DOWNLOAD: отправляет файл клиенту.
 */
public class DownloadCommand implements Command<ServerCommandContext> {
    private final Path serverDir;
    private final Logger log = LoggerFactory.getLogger(DownloadCommand.class);

    public DownloadCommand(Path serverDir) {
        this.serverDir = serverDir;
    }

    @Override
    public String name() {
        return CommandName.DOWNLOAD.key();
    }

    @Override
    public CommandResult execute(String[] args, ServerCommandContext ctx) throws IOException {
        if (args.length < 1) {
            ctx.writeLine("ОШИБКА: имя файла не указано");
            return CommandResult.ERROR;
        }
        String filename = args[0];
        Path source = serverDir.resolve(filename);

        if (!Files.exists(source)) {
            ctx.writeLine("ОШИБКА: файл не найден: " + filename);
            log.warn("Запрошен несуществующий файл '{}'", filename);
            return CommandResult.ERROR;
        }

        long size = Files.size(source);

        ctx.writeLine("ОК");
        ctx.writeLine(String.valueOf(size));
        log.info("Готов отправлять файл '{}' размером {} байт", filename, size);

        long start = System.currentTimeMillis();
        try (InputStream fis = Files.newInputStream(source)) {
            IoUtils.copyStream(fis, ctx.out(), size);
        }
        long elapsed = System.currentTimeMillis() - start;

        String rate = IoUtils.formatTransferRate(size, elapsed);
        log.info("Файл '{}' отправлен: {} ({} байт за {} мс)", filename, rate, size, elapsed);

        ctx.writeLine("Файл '" + filename + "' отправлен: " + rate);
        return CommandResult.CONTINUE;
    }
}
