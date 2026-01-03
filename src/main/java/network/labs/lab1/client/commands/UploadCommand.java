package network.labs.lab1.client.commands;

import network.labs.lab1.common.*;
import network.labs.lab1.client.ClientCommandContext;

import java.io.*;
import java.nio.file.*;

/**
 * Команда загрузки файла на сервер.
 */
public class UploadCommand implements Command<ClientCommandContext> {
    @Override
    public String name() {
        return CommandName.UPLOAD.key();
    }

    @Override
    public CommandResult execute(String[] args, ClientCommandContext ctx) throws IOException {
        if (args.length < 1) {
            System.out.println("Использование: UPLOAD <имя файла>");
            return CommandResult.ERROR;
        }
        String filename = args[0];
        Path file = ctx.clientDir().resolve(filename);

        if (!Files.exists(file)) {
            System.out.println("Файл не найден: " + file.toAbsolutePath());
            return CommandResult.ERROR;
        }

        IoUtils.writeLine(ctx.out(), CommandName.UPLOAD.key() + " " + filename);
        String response = IoUtils.readLine(ctx.in());
        if (response == null || response.startsWith("ОШИБКА")) {
            System.out.println("Сервер: " + response);
            return CommandResult.ERROR;
        }

        long fileSize = Files.size(file);
        IoUtils.writeLine(ctx.out(), String.valueOf(fileSize));

        try (InputStream fis = Files.newInputStream(file)) {
            IoUtils.copyStream(fis, ctx.out(), fileSize);
        }

        String finalResponse = IoUtils.readLine(ctx.in());
        System.out.println("Сервер: " + finalResponse);

        return CommandResult.CONTINUE;
    }
}
