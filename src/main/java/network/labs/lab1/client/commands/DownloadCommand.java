package network.labs.lab1.client.commands;

import network.labs.lab1.common.*;
import java.io.*;
import java.nio.file.*;

public class DownloadCommand implements Command<FileAwareContext> {
    @Override
    public String name() {
        return CommandName.DOWNLOAD.key();
    }

    @Override
    public CommandResult execute(String[] args, FileAwareContext ctx) throws IOException {
        if (args.length < 1) {
            System.out.println("Использование: DOWNLOAD <имя файла>");
            return CommandResult.ERROR;
        }
        String filename = args[0];

        ctx.writeLine(CommandName.DOWNLOAD.key() + " " + filename);
        String status = ctx.readLine();
        if (status == null || status.startsWith("ОШИБКА")) {
            System.out.println("Сервер: " + status);
            return CommandResult.ERROR;
        }
        if (!"ОК".equals(status)) {
            System.out.println("Неожиданный ответ сервера: " + status);
            return CommandResult.ERROR;
        }

        String sizeLine = ctx.readLine();
        long fileSize = Long.parseLong(sizeLine.trim());
        Path target = ctx.filesDir().resolve("downloaded_" + filename);

        try (OutputStream fos = Files.newOutputStream(target)) {
            IoUtils.copyStream(ctx.inputStream(), fos, fileSize);
        }

        String finalMsg = ctx.readLine();
        System.out.println("Сервер: " + finalMsg);
        System.out.println("Файл сохранён: " + target.getFileName());
        return CommandResult.CONTINUE;
    }
}