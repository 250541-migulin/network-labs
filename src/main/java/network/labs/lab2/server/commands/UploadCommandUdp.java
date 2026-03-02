package network.labs.lab2.server.commands;

import network.labs.lab1.common.IoUtils;
import network.labs.lab2.core.UdpCommand;
import network.labs.lab2.transport.ReliableUdpReceiver;
import network.labs.lab2.util.UdpIo;
import network.labs.lab2.server.UdpServer;
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
    private final UdpServer server; // ← новый параметр
    private final Logger log = LoggerFactory.getLogger(UploadCommandUdp.class);

    public UploadCommandUdp(Path serverDir, UdpServer server) {
        this.serverDir = serverDir;
        this.server = server;
    }

    @Override
    public String name() {
        return "UPLOAD";
    }

    @Override
    public void execute(String[] args, DatagramSocket socket, InetSocketAddress peer) throws IOException {
        if (args.length < 1) {
            UdpIo.sendLine(socket, peer, "ERROR имя файла не указано");
            return;
        }

        String filename = args[0];
        Path target = serverDir.resolve(filename);
        Files.createDirectories(serverDir);

        log.info("UPLOAD: запрос на загрузку файла '{}'", filename);

        // === Проверка на докачку ===
        long offset = 0;
        boolean isNewFile = !Files.exists(target);

        if (!isNewFile) {
            if (server.isSameClientAndFile(peer, filename)) {
                offset = Files.size(target);
                log.info("Возобновляем загрузку '{}' (уже {} байт)", filename, offset);
            } else {
                Files.delete(target);
                isNewFile = true;
                log.info("Удалён файл '{}' (другой клиент)", filename);
            }
        }

        // === Отправляем offset ===
        UdpIo.sendLine(socket, peer, "OK " + offset);
        log.debug("UPLOAD: отправлен OK {}", offset);

        // === Ждём размер остатка ===
        String sizeLine = UdpIo.receiveLine(socket);
        long remaining;
        try {
            remaining = Long.parseLong(sizeLine.trim());
        } catch (NumberFormatException e) {
            UdpIo.sendLine(socket, peer, "ERROR неверный размер");
            log.error("UPLOAD: неверный формат размера '{}'", sizeLine);
            return;
        }

        if (remaining < 0) {
            UdpIo.sendLine(socket, peer, "ERROR отрицательный размер");
            return;
        }

        UdpIo.sendLine(socket, peer, "READY");
        log.debug("UPLOAD: ожидаем {} байт", remaining);

        // === Приём данных ===
        long start = System.currentTimeMillis();
        try (FileOutputStream fos = new FileOutputStream(target.toFile(), true)) { // append = true
            new ReliableUdpReceiver(socket, peer, fos).receiveStream(remaining); // ← передаём размер!
        } catch (IOException e) {
            // Сохраняем сессию для восстановления
            log.warn("UPLOAD прерван: {}", e.getMessage());
            server.setLastSession(peer, filename);
            return;
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("UPLOAD: файл '{}' принят за {} мс", filename, elapsed);

        // Успешно — сбрасываем сессию
        server.setLastSession(null, null);

        String rate = IoUtils.formatTransferRate(Files.size(target), elapsed);
        UdpIo.sendLine(socket, peer, "DONE Файл '" + filename + "' загружен: " + rate);
        log.info("UPLOAD: завершено");
    }
}