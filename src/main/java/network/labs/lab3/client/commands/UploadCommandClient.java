package network.labs.lab3.client.commands;

import network.labs.lab2.util.PathsConfig;
import network.labs.lab3.client.*;
import network.labs.lab3.server.Protocol;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class UploadCommandClient implements ClientCommand {
    @Override
    public String name() { return "UPLOAD"; }

    @Override
    public void execute(String[] args, ClientContext ctx) throws IOException {
        if (args.length < 2) {
            System.out.println("❌ нужно указать файл: UPLOAD <filename>");
            return;
        }
        String filename = args[1];
        Path filePath = PathsConfig.CLIENT_TCP.resolve(filename);

        FileUtils.ensureDirectory(PathsConfig.CLIENT_TCP);

        if (!Files.exists(filePath)) {
            System.out.println("❌ Файл не найден: " + filePath.toAbsolutePath());
            return;
        }

        ctx.sendLine("UPLOAD " + filename);
        String resp = ctx.readLine();
        System.out.println("⬅️ сервер: " + resp);

        if (resp == null || !resp.equals(Protocol.READY)) {
            System.out.println("❌ сервер не готов к приёму файла");
            return;
        }

        try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
            byte[] buf = new byte[Protocol.TCP_CHUNK_SIZE];
            int read;
            while ((read = fis.read(buf)) != -1) {
                ctx.sendLine(new String(buf, 0, read));
            }
        }

        ctx.sendLine(Protocol.DONE);
        String finalMsg = ctx.readLine();
        System.out.println("⬅️ сервер: " + finalMsg);
        System.out.println("✅ файл отправлен: " + filePath.toAbsolutePath());
    }
}
