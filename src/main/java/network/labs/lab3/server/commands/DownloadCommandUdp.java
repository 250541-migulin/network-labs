package network.labs.lab3.server.commands;

import network.labs.lab3.server.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DownloadCommandUdp implements Command<UdpNioContext> {
    private static final Logger log = LoggerFactory.getLogger(DownloadCommandUdp.class);
    private final Path serverDir;

    public DownloadCommandUdp(Path serverDir) {
        this.serverDir = serverDir;
    }

    @Override
    public String name() { return "DOWNLOAD"; }

    @Override
    public void execute(String[] args, UdpNioContext ctx) throws IOException {
        if (args.length < 2) {
            ctx.sendLine("UDP DOWNLOAD: filename required");
            return;
        }
        String filename = args[1];
        Path filePath = serverDir.resolve(filename);

        if (!Files.exists(filePath)) {
            ctx.sendLine("UDP DOWNLOAD: file not found -> " + filename);
            return;
        }

        ctx.sendLine(Protocol.READY);

        long totalBytes = 0;
        int chunkIndex = 0;

        try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
            byte[] buf = new byte[ctx.chunkSize()];
            int read;
            while ((read = fis.read(buf)) != -1) {
                ctx.sendChunk(buf, 0, read);
                totalBytes += read;
                chunkIndex++;
                log.debug("UDP: отправлен блок #{}: {} байт (сумма: {})", chunkIndex, read, totalBytes);
            }
        }

        log.info("UDP: файл '{}' отправлен: {} байт", filename, totalBytes);
        ctx.sendLine(Protocol.DONE);
    }
}
