package network.labs.lab2.client.commands;

import network.labs.lab1.common.IoUtils;
import network.labs.lab2.core.LimitInputStream;
import network.labs.lab2.core.UdpCommand;
import network.labs.lab2.transport.ReliableUdpSender;
import network.labs.lab2.util.PathsConfig;
import network.labs.lab2.util.UdpIo;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

public class UploadCommandUdp implements UdpCommand {
    @Override
    public String name() { return "UPLOAD"; }

    @Override
    public void execute(String[] args, DatagramSocket socket, InetSocketAddress server) throws IOException {
        if (args.length < 1) {
            System.out.println("Использование: UPLOAD <имя файла>");
            return;
        }

        Path clientDir = PathsConfig.CLIENT_UDP;
        Files.createDirectories(clientDir);
        String filename = args[0];
        Path file = clientDir.resolve(filename);

        if (!Files.exists(file)) {
            System.out.println("Файл не найден: " + file.toAbsolutePath());
            return;
        }

        long fileSize = Files.size(file);

        // === Фаза 1: отправка имени ===
        UdpIo.sendLine(socket, server, "UPLOAD " + filename);

        // === Фаза 2: получение offset ===
        String ok = UdpIo.receiveLine(socket);
        if (!ok.startsWith("OK ")) {
            System.out.println("Сервер: " + ok);
            return;
        }

        long offset;
        try {
            offset = Long.parseLong(ok.substring("OK ".length()).trim());
        } catch (NumberFormatException e) {
            System.out.println("Сервер: некорректный offset");
            return;
        }

        long remaining = fileSize - offset;
        if (remaining <= 0) {
            System.out.println("Файл уже загружен на сервере.");
            return;
        }

        // === Фаза 3: отправка размера остатка ===
        UdpIo.sendLine(socket, server, String.valueOf(remaining));
        String ready = UdpIo.receiveLine(socket);
        if (!"READY".equals(ready)) {
            System.out.println("Сервер: " + ready);
            return;
        }

        // === Фаза 4: передача ===
        System.out.println("Загрузка файла '" + filename + "' с offset=" + offset + "...");
        long start = System.currentTimeMillis();
        try (FileInputStream fis = new FileInputStream(file.toFile())) {
            fis.skipNBytes(offset);
            try (InputStream limited = new LimitInputStream(fis, remaining)) {
                new ReliableUdpSender(socket, server).sendStream(limited, remaining);
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("Отправлено: " + IoUtils.formatTransferRate(remaining, elapsed));

        // === Фаза 5: финал ===
        String finalMsg = UdpIo.receiveLine(socket);
        System.out.println("Сервер: " + finalMsg);
    }
}