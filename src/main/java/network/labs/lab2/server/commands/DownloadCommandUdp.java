package network.labs.lab2.server.commands;

import network.labs.lab1.common.IoUtils;
import network.labs.lab2.core.UdpCommand;
import network.labs.lab2.transport.ReliableUdpSender;
import network.labs.lab2.util.UdpIo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

public class DownloadCommandUdp implements UdpCommand {
    private final Path serverDir;

    private final Logger log = LoggerFactory.getLogger(DownloadCommandUdp.class);

    public DownloadCommandUdp(Path serverDir) {
        this.serverDir = serverDir;
    }

    @Override
    public String name() { return "DOWNLOAD"; }

    @Override
    public void execute(String[] args, DatagramSocket socket, InetSocketAddress peer) throws IOException {
        if (args.length < 1) {
            UdpIo.sendLine(socket, peer, "CTRL:ERROR имя файла не указано");
            return;
        }

        String filename = args[0];
        Path source = serverDir.resolve(filename);

        if (!Files.exists(source)) {
            UdpIo.sendLine(socket, peer, "CTRL:ERROR файл не найден — " + filename);
            log.warn("DOWNLOAD: файл '{}' не найден", filename);
            return;
        }

        long size = Files.size(source);
        log.info("DOWNLOAD: запрос на скачивание '{}', размер {} байт", filename, size);

        // Фаза 1: контрольные сообщения
        UdpIo.sendLine(socket, peer, "CTRL:OK " + size);
        UdpIo.sendLine(socket, peer, "CTRL:READY");
        log.debug("DOWNLOAD: отправлены CTRL:OK и CTRL:READY");

        // Фаза 2: отправка файла
        long start = System.currentTimeMillis();
        try (FileInputStream fis = new FileInputStream(source.toFile())) {
            new ReliableUdpSender(socket, peer).sendStream(fis, size);
        }
        long elapsed = System.currentTimeMillis() - start;
        log.info("DOWNLOAD: файл '{}' отправлен за {} мс", filename, elapsed);

        // Фаза 3: завершение
        String rate = IoUtils.formatTransferRate(size, elapsed);
        UdpIo.sendLine(socket, peer, "CTRL:DONE Файл '" + filename + "' отправлен: " + rate);
        log.info("DOWNLOAD: завершено, отправлен CTRL:DONE");
    }

}