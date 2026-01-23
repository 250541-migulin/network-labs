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
        Path target = ctx.filesDir().resolve("downloaded_" + filename);

        long localSize = Files.exists(target) ? Files.size(target) : 0;
        if (localSize > 0) {
            System.out.println("Обнаружен частично скачанный файл (" + localSize + " байт). Продолжаем докачку.");
        }

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
        System.out.println("Сервер отправит " + fileSize + " байт");

        // Открываем файл в режиме дозаписи
        try (OutputStream fos = Files.newOutputStream(target,
                StandardOpenOption.CREATE,
                localSize > 0 ? StandardOpenOption.APPEND : StandardOpenOption.WRITE)) {

            // Пропускаем уже полученные байты
            IoUtils.skipStream(ctx.inputStream(), localSize);

            long totalToReceive = fileSize - localSize;
            long received = 0;
            byte[] buffer = new byte[8192];
            int lastPercent = -1;

            while (received < totalToReceive) {
                int toRead = (int) Math.min(buffer.length, totalToReceive - received);
                int read = ctx.inputStream().read(buffer, 0, toRead);
                if (read == -1) break;

                fos.write(buffer, 0, read);
                received += read;

                int percent = (int) ((localSize + received) * 100 / fileSize);
                if (percent != lastPercent && percent % 10 == 0) {
                    System.out.println("Прогресс: " + percent + "% (" + (localSize + received) + " / " + fileSize + " байт)");
                    lastPercent = percent;
                }
            }
        }

        String finalMsg = ctx.readLine();
        System.out.println("Сервер: " + finalMsg);
        System.out.println("Файл сохранён: " + target.getFileName());
        return CommandResult.CONTINUE;
    }
}