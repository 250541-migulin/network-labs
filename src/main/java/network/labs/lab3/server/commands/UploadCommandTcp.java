package network.labs.lab3.server.commands;

import network.labs.lab3.server.*;
import network.labs.lab1.common.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

public class UploadCommandTcp implements Command<TcpNioContext> {
    private static final Logger log = LoggerFactory.getLogger(UploadCommandTcp.class);
    private final Path serverDir;

    public UploadCommandTcp(Path serverDir) {
        this.serverDir = serverDir;
    }

    @Override
    public String name() { return "UPLOAD"; }

    @Override
    public void execute(String[] args, TcpNioContext ctx) throws IOException {
        if (args.length < 2) {
            ctx.writeLine("TCP UPLOAD: filename required");
            return;
        }
        String filename = args[1];
        Path filePath = serverDir.resolve(filename);

        FileUtils.ensureDirectory(serverDir);
        ctx.writeLine(Protocol.READY);

        long totalBytes = 0;
        int chunkIndex = 0;

        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            while (true) {
                String chunk = ctx.readLine();
                if (chunk == null) {
                    log.warn("Соединение закрыто клиентом во время загрузки '{}'", filename);
                    break;
                }
                if (chunk.equals(Protocol.DONE)) {
                    log.info("Файл '{}' успешно получен: {} байт", filename, totalBytes);
                    break;
                }
                byte[] data = chunk.getBytes();
                fos.write(data);
                totalBytes += data.length;
                chunkIndex++;
                log.debug("Принят блок #{}: {} байт (сумма: {})", chunkIndex, data.length, totalBytes);
            }
        }

        ctx.writeLine("✅ TCP UPLOAD завершён: " + filename + " (" + totalBytes + " байт)");
    }
}
