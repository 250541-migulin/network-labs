package network.labs.lab2.server.commands;

import network.labs.lab1.common.IoUtils;
import network.labs.lab2.core.LimitInputStream;
import network.labs.lab2.core.UdpCommand;
import network.labs.lab2.transport.ReliableUdpSender;
import network.labs.lab2.util.UdpIo;
import network.labs.lab2.server.UdpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

public class DownloadCommandUdp implements UdpCommand {
    private final Path serverDir;
    private final UdpServer server;
    private final Logger log = LoggerFactory.getLogger(DownloadCommandUdp.class);

    public DownloadCommandUdp(Path serverDir, UdpServer server) {
        this.serverDir = serverDir;
        this.server = server;
    }

    @Override
    public String name() { return "DOWNLOAD"; }

    @Override
    public void execute(String[] args, DatagramSocket socket, InetSocketAddress peer) throws IOException {
        if (args.length < 1) {
            UdpIo.sendLine(socket, peer, "ERROR имя файла не указано");
            return;
        }

        String filename = args[0];
        long requestedOffset = 0;
        if (args.length >= 2) {
            try {
                requestedOffset = Long.parseLong(args[1]);
            } catch (NumberFormatException e) {
                UdpIo.sendLine(socket, peer, "ERROR некорректный offset");
                return;
            }
        }

        Path source = serverDir.resolve(filename);
        if (!Files.exists(source)) {
            UdpIo.sendLine(socket, peer, "ERROR файл не найден — " + filename);
            log.warn("DOWNLOAD: файл '{}' не найден", filename);
            return;
        }

        long fileSize = Files.size(source);
        long actualOffset = Math.min(requestedOffset, fileSize);
        long remaining = fileSize - actualOffset;

        log.info("DOWNLOAD: {} запрашивает '{}' с offset={} (осталось {} байт)", peer, filename, actualOffset, remaining);

        UdpIo.sendLine(socket, peer, "OK " + fileSize);
        UdpIo.sendLine(socket, peer, "READY");

        if (remaining == 0) {
            UdpIo.sendLine(socket, peer, "DONE Файл '" + filename + "' уже полный");
            return;
        }

        long start = System.currentTimeMillis();
        try (FileInputStream fis = new FileInputStream(source.toFile())) {
            fis.skipNBytes(actualOffset);
            try (InputStream limited = new LimitInputStream(fis, remaining)) {
                new ReliableUdpSender(socket, peer).sendStream(limited, remaining);
            }
        } catch (IOException e) {
            log.warn("DOWNLOAD прерван: {}", e.getMessage());
            server.setLastSession(peer, filename);
            return;
        }

        long elapsed = System.currentTimeMillis() - start;
        String rate = IoUtils.formatTransferRate(remaining, elapsed);
        UdpIo.sendLine(socket, peer, "DONE Файл '" + filename + "' отправлен: " + rate);
        log.info("DOWNLOAD: завершено");
    }
}