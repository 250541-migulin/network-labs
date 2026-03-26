package network.labs.lab3.client.commands;

import network.labs.lab2.util.PathsConfig;
import network.labs.lab3.client.UdpClientCommand;
import network.labs.lab3.client.UdpClientContext;
import network.labs.lab3.server.Protocol;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class UploadCommandUdp implements UdpClientCommand {
    @Override
    public String name() { return "UPLOAD"; }

    @Override
    public void execute(String[] args, UdpClientContext ctx) throws IOException {
        if (args.length < 2) {
            System.out.println("❌ нужно указать файл: UPLOAD <filename>");
            return;
        }
        String filename = args[1];
        Path filePath = PathsConfig.CLIENT_UDP.resolve(filename);

        FileUtils.ensureDirectory(PathsConfig.CLIENT_UDP);

        if (!Files.exists(filePath)) {
            System.out.println("❌ Файл не найден: " + filePath.toAbsolutePath());
            return;
        }

        ctx.sendLine("UPLOAD " + filename);
        String resp = ctx.readLine();
        System.out.println("⬅️ сервер: " + resp);

        if (resp == null || !resp.equals(Protocol.READY)) {
            System.out.println("❌ сервер не готов к приёму файла (UDP)");
            return;
        }

        try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
            byte[] buf = new byte[ctx.chunkSize()];
            int read;
            int chunkIndex = 0;
            while ((read = fis.read(buf)) != -1) {
                ctx.sendChunk(buf, 0, read);
                chunkIndex++;
                System.out.println("➡️ отправлен блок #" + chunkIndex + ": " + read + " байт");
            }
        }

        ctx.sendLine(Protocol.DONE);
        String finalMsg = ctx.readLine();
        System.out.println("⬅️ сервер: " + finalMsg);
        System.out.println("✅ файл отправлен (UDP): " + filePath.toAbsolutePath());
    }
}
