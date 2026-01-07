package network.labs.lab2.client.commands;

import network.labs.lab1.common.IoUtils;
import network.labs.lab2.core.UdpCommand;
import network.labs.lab2.transport.ReliableUdpSender;
import network.labs.lab2.util.PathsConfig;
import network.labs.lab2.util.UdpIo;

import java.io.FileInputStream;
import java.io.IOException;
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

        long size = Files.size(file);

        // ✅ ФАЗА 1: отправляем команду
        UdpIo.sendLine(socket, server, "UPLOAD " + filename);

        // ✅ ФАЗА 2: ждём подтверждение
        String ok = UdpIo.receiveLine(socket);
        if (!"CTRL:OK".equals(ok)) {
            System.out.println("Сервер: " + ok);
            return;
        }

        // ✅ ФАЗА 3: отправляем размер
        UdpIo.sendLine(socket, server, String.valueOf(size));
        String ready = UdpIo.receiveLine(socket);
        if (!"CTRL:READY".equals(ready)) {
            System.out.println("Сервер: " + ready);
            return;
        }

        // ✅ ФАЗА 4: передаём файл
        System.out.println("Загрузка файла '" + filename + "'...");
        long start = System.currentTimeMillis();
        try (FileInputStream fis = new FileInputStream(file.toFile())) {
            new ReliableUdpSender(socket, server).sendStream(fis, size);
        }
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("✅ Отправлено: " + IoUtils.formatTransferRate(size, elapsed));

        // ✅ ФАЗА 5: ждём финальное сообщение
        String finalMsg = UdpIo.receiveLine(socket);
        System.out.println("Сервер: " + finalMsg);
    }

}