package network.labs.lab3.client.commands;

import network.labs.lab2.util.PathsConfig;
import network.labs.lab1.common.FileUtils;
import network.labs.lab3.client.UdpClientCommand;
import network.labs.lab3.client.UdpClientContext;
import network.labs.lab3.server.Protocol;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

public class DownloadCommandUdp implements UdpClientCommand {
    @Override
    public String name() { return "DOWNLOAD"; }

    @Override
    public void execute(String[] args, UdpClientContext ctx) throws IOException {
        if (args.length < 2) {
            System.out.println("нужно указать файл: DOWNLOAD <filename>");
            return;
        }
        String filename = args[1];
        Path filePath = PathsConfig.CLIENT_UDP.resolve(filename);

        FileUtils.ensureDirectory(PathsConfig.CLIENT_UDP);

        ctx.sendLine("DOWNLOAD " + filename);
        String resp = ctx.readLine();
        System.out.println("⬅️ сервер: " + resp);

        if (resp == null || !resp.equals(Protocol.READY)) {
            System.out.println("сервер не готов к передаче файла (UDP)");
            return;
        }

        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            int chunkIndex = 0;
            while (true) {
                byte[] data = ctx.readChunk();
                if (data == null) {
                    System.out.println("соединение закрыто сервером");
                    break;
                }
                String marker = new String(data).trim();
                if (marker.equals(Protocol.DONE)) {
                    System.out.println("📥 получен маркер завершения передачи");
                    break;
                }
                fos.write(data);
                chunkIndex++;
                System.out.println("⬅️ получен блок #" + chunkIndex + ": " + data.length + " байт");
            }
        }

        System.out.println("файл получен (UDP): " + filePath.toAbsolutePath());
    }
}
