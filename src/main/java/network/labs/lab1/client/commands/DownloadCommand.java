package network.labs.lab1.client.commands;

import network.labs.lab1.common.*;
import network.labs.lab1.client.ClientCommandContext;

import java.io.*;
import java.nio.file.*;

/**
 * Команда скачивания файла с сервера.
 */
public class DownloadCommand implements Command<ClientCommandContext> {
    @Override
    public String name() {
        return CommandName.DOWNLOAD.key();
    }

    @Override
    public CommandResult execute(String[] args, ClientCommandContext ctx) throws IOException {
        if (args.length < 1) {
            System.out.println("Использование: DOWNLOAD <имя файла>");
            return CommandResult.ERROR;
        }
        String filename = args[0];

        IoUtils.writeLine(ctx.out(), CommandName.DOWNLOAD.key() + " " + filename);
        String status = IoUtils.readLine(ctx.in());
        if (status == null || status.startsWith("ОШИБКА")) {
            System.out.println("Сервер: " + status);
            return CommandResult.ERROR;
        }
        if (!"ОК".equals(status)) {
            System.out.println("Неожиданный ответ сервера: " + status);
            return CommandResult.ERROR;
        }

        String sizeLine = IoUtils.readLine(ctx.in());
        long fileSize = Long.parseLong(sizeLine.trim());
        Path target = ctx.clientDir().resolve("client_" + filename);

        try (OutputStream fos = Files.newOutputStream(target)) {
            IoUtils.copyStream(ctx.in(), fos, fileSize);
        }

        String finalMsg = IoUtils.readLine(ctx.in());
        System.out.println("Сервер: " + finalMsg);
        System.out.println("Файл сохранён: " + target.getFileName());

        return CommandResult.CONTINUE;
    }
}
