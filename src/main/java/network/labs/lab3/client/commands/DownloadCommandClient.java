package network.labs.lab3.client.commands;

import network.labs.lab2.util.PathsConfig;
import network.labs.lab1.common.FileUtils;
import network.labs.lab3.client.*;
import network.labs.lab3.server.Protocol;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

public class DownloadCommandClient implements ClientCommand {
    @Override
    public String name() { return "DOWNLOAD"; }

    @Override
    public void execute(String[] args, ClientContext ctx) throws IOException {
        if (args.length < 2) {
            System.out.println("❌ нужно указать файл: DOWNLOAD <filename>");
            return;
        }
        String filename = args[1];
        Path filePath = PathsConfig.CLIENT_TCP.resolve(filename);

        FileUtils.ensureDirectory(PathsConfig.CLIENT_TCP);

        ctx.sendLine("DOWNLOAD " + filename);
        String resp = ctx.readLine();
        System.out.println("⬅️ сервер: " + resp);

        if (resp == null || !resp.equals(Protocol.READY)) {
            System.out.println("❌ сервер не готов к передаче файла");
            return;
        }

        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            String chunk;
            while ((chunk = ctx.readLine()) != null) {
                if (chunk.equals(Protocol.DONE)) break;
                fos.write(chunk.getBytes());
            }
        }

        System.out.println("✅ файл получен: " + filePath.toAbsolutePath());
    }
}
