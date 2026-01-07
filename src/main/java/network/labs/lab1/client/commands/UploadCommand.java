package network.labs.lab1.client.commands;

import network.labs.lab1.common.*;
import java.io.*;
import java.nio.file.*;

public class UploadCommand implements Command<FileAwareContext> {
    @Override
    public String name() {
        return CommandName.UPLOAD.key();
    }

    @Override
    public CommandResult execute(String[] args, FileAwareContext ctx) throws IOException {
        if (args.length < 1) {
            System.out.println("Использование: UPLOAD <имя файла>");
            return CommandResult.ERROR;
        }
        String filename = args[0];
        Path file = ctx.filesDir().resolve(filename);

        if (!Files.exists(file)) {
            System.out.println("Файл не найден: " + file.toAbsolutePath());
            return CommandResult.ERROR;
        }

        ctx.writeLine(CommandName.UPLOAD.key() + " " + filename);
        String response = ctx.readLine();
        if (response == null || response.startsWith("ОШИБКА")) {
            System.out.println("Сервер: " + response);
            return CommandResult.ERROR;
        }

        long fileSize = Files.size(file);
        ctx.writeLine(String.valueOf(fileSize));

        try (InputStream fis = Files.newInputStream(file)) {
            IoUtils.copyStream(fis, ctx.outputStream(), fileSize);
        }

        String finalResponse = ctx.readLine();
        System.out.println("Сервер: " + finalResponse);
        return CommandResult.CONTINUE;
    }
}