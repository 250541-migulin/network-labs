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

        long fileSize = Files.size(file);
        ctx.writeLine(CommandName.UPLOAD.key() + " " + filename);

        // Получаем offset от сервера
        String response = ctx.readLine();
        if (response == null || !response.startsWith("OK")) {
            System.out.println("Сервер: " + (response != null ? response : "соединение закрыто"));
            return CommandResult.ERROR;
        }

        long offset = 0;
        if (response.length() > 3) {
            try {
                offset = Long.parseLong(response.substring(3).trim());
            } catch (NumberFormatException e) {
                System.out.println("Сервер: некорректный offset");
                return CommandResult.ERROR;
            }
        }

        if (offset > fileSize) {
            System.out.println("Сервер: offset больше размера локального файла");
            return CommandResult.ERROR;
        }

        long remaining = fileSize - offset;
        if (remaining == 0) {
            System.out.println("Файл уже полностью загружен на сервере!");
            return CommandResult.CONTINUE;
        }

        System.out.println("Сервер готов принять " + remaining + " байт (всего: " + fileSize + " байт)");

        // Отправляем размер остатка
        ctx.writeLine(String.valueOf(remaining));

        // Отправляем данные
        try (InputStream fis = Files.newInputStream(file)) {
            fis.skipNBytes(offset); // пропускаем уже отправленное

            long sent = 0;
            byte[] buffer = new byte[8192];
            int lastPercent = -1;

            while (sent < remaining) {
                int toSend = (int) Math.min(buffer.length, remaining - sent);
                int read = fis.read(buffer, 0, toSend);
                if (read == -1) break;

                ctx.outputStream().write(buffer, 0, read);
                ctx.outputStream().flush();
                sent += read;

                int percent = (int) ((offset + sent) * 100 / fileSize);
                if (percent != lastPercent && percent % 10 == 0) {
                    System.out.println("Прогресс: " + percent + "% (" + (offset + sent) + " / " + fileSize + " байт)");
                    lastPercent = percent;
                }
            }
        }

        String finalResponse = ctx.readLine();
        System.out.println("Сервер: " + finalResponse);
        return CommandResult.CONTINUE;
    }
}