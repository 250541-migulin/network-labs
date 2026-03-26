package network.labs.lab3.server.commands;

import network.labs.lab3.server.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

public class UploadCommandUdp implements Command<UdpNioContext> {
    private static final Logger log = LoggerFactory.getLogger(UploadCommandUdp.class);
    private final Path serverDir;

    public UploadCommandUdp(Path serverDir) {
        this.serverDir = serverDir;
    }

    @Override
    public String name() { return "UPLOAD"; }

    @Override
    public void execute(String[] args, UdpNioContext ctx) throws IOException {
        if (args.length < 2) {
            ctx.sendLine("UDP UPLOAD: filename required");
            return;
        }
        String filename = args[1];
        Path filePath = serverDir.resolve(filename);

        FileUtils.ensureDirectory(serverDir);
        ctx.sendLine(Protocol.READY);

        long totalBytes = 0;
        int chunkIndex = 0;

        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            while (true) {
                String line = ctx.receiveLine();
                if (line == null) {
                    log.warn("UDP: клиент закрыл соединение во время загрузки '{}'", filename);
                    break;
                }
                if (line.equals(Protocol.DONE)) {
                    log.info("UDP: файл '{}' успешно получен: {} байт", filename, totalBytes);
                    break;
                }
                byte[] data = line.getBytes();
                fos.write(data);
                totalBytes += data.length;
                chunkIndex++;
                log.debug("UDP: принят блок #{}: {} байт (сумма: {})", chunkIndex, data.length, totalBytes);
            }
        }

        ctx.sendLine("✅ UDP UPLOAD завершён: " + filename + " (" + totalBytes + " байт)");
    }
}
