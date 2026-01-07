package network.labs.lab2.server.commands;

import network.labs.lab1.common.IoUtils;
import network.labs.lab2.core.UdpCommand;
import network.labs.lab2.transport.ReliableUdpReceiver;
import network.labs.lab2.util.UdpIo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

public class UploadCommandUdp implements UdpCommand {
    private final Path serverDir;

    private final Logger log = LoggerFactory.getLogger(UploadCommandUdp.class);

    public UploadCommandUdp(Path serverDir) {
        this.serverDir = serverDir;
    }

    @Override
    public String name() {
        return "UPLOAD";
    }

    @Override
    public void execute(String[] args, DatagramSocket socket, InetSocketAddress peer) throws IOException {
        if (args.length < 1) {
            UdpIo.sendLine(socket, peer, "CTRL:ERROR имя файла не указано");
            return;
        }

        String filename = args[0];
        Path target = serverDir.resolve(filename);
        Files.createDirectories(serverDir);

        log.info("UPLOAD: запрос на загрузку файла '{}'", filename);

        // Фаза 1: подтверждаем команду
        UdpIo.sendLine(socket, peer, "CTRL:OK");
        log.debug("UPLOAD: отправлен CTRL:OK");

        // Фаза 2: ждём размер
        String sizeLine = UdpIo.receiveLine(socket);
        log.debug("UPLOAD: получена строка размера '{}'", sizeLine);

        long size;
        try {
            size = Long.parseLong(sizeLine.trim());
        } catch (NumberFormatException e) {
            UdpIo.sendLine(socket, peer, "CTRL:ERROR неверный размер");
            log.error("UPLOAD: неверный формат размера '{}'", sizeLine);
            return;
        }

        UdpIo.sendLine(socket, peer, "CTRL:READY");
        log.debug("UPLOAD: отправлен CTRL:READY, ожидаем {} байт", size);

        // Фаза 3: приём файла
        long start = System.currentTimeMillis();
        try (FileOutputStream fos = new FileOutputStream(target.toFile())) {
            new ReliableUdpReceiver(socket, peer, fos).receiveStream();
        }
        long elapsed = System.currentTimeMillis() - start;
        log.info("UPLOAD: файл '{}' принят за {} мс", filename, elapsed);

        // Фаза 4: финал
        String rate = IoUtils.formatTransferRate(size, elapsed);
        UdpIo.sendLine(socket, peer, "CTRL:DONE Файл '" + filename + "' загружен: " + rate);
        log.info("UPLOAD: завершено, отправлен CTRL:DONE");
    }

}