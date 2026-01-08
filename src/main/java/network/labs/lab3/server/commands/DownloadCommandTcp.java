package network.labs.lab3.server.commands;

import network.labs.lab3.server.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DownloadCommandTcp implements Command<TcpNioContext> {
    private final Path serverDir;

    public DownloadCommandTcp(Path serverDir) {
        this.serverDir = serverDir;
    }

    @Override
    public String name() { return "DOWNLOAD"; }

    @Override
    public void execute(String[] args, TcpNioContext ctx) throws IOException {
        if (args.length < 2) {
            ctx.writeLine("TCP DOWNLOAD: filename required");
            return;
        }
        String filename = args[1];
        Path filePath = serverDir.resolve(filename);

        if (!Files.exists(filePath)) {
            ctx.writeLine("TCP DOWNLOAD: file not found -> " + filename);
            return;
        }

        ctx.writeLine(Protocol.CTRL_READY);

        try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
            byte[] buf = new byte[ctx.chunkSize()];
            int read;
            while ((read = fis.read(buf)) != -1) {
                ctx.writeBytes(buf, 0, read);
            }
        }

        ctx.writeLine(Protocol.CTRL_DONE);
    }
}
