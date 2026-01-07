package network.labs.lab2.client.commands;

import network.labs.lab1.common.IoUtils;
import network.labs.lab2.core.UdpCommand;
import network.labs.lab2.transport.ReliableUdpReceiver;
import network.labs.lab2.util.PathsConfig;
import network.labs.lab2.util.UdpIo;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

public class DownloadCommandUdp implements UdpCommand {
    @Override
    public String name() { return "DOWNLOAD"; }

    @Override
    public void execute(String[] args, DatagramSocket socket, InetSocketAddress server) throws IOException {
        if (args.length < 1) {
            System.out.println("Использование: DOWNLOAD <имя файла>");
            return;
        }

        Path clientDir = PathsConfig.CLIENT_UDP;
        Files.createDirectories(clientDir);
        String filename = args[0];
        Path target = clientDir.resolve("downloaded_" + filename);

        // Фаза 1: запрос
        UdpIo.sendLine(socket, server, "DOWNLOAD " + filename);

        String ok = UdpIo.receiveLine(socket);
        if (!ok.startsWith("CTRL:OK ")) {
            System.out.println("Сервер: " + ok);
            return;
        }

        long size = Long.parseLong(ok.split("\\s+", 2)[1]);
        String ready = UdpIo.receiveLine(socket);
        if (!"CTRL:READY".equals(ready)) {
            System.out.println("Сервер: " + ready);
            return;
        }

        // Фаза 2: приём файла НАДЁЖНО
        System.out.println("Скачивание файла '" + filename + "' (" + size + " байт)...");
        long start = System.currentTimeMillis();

        try (FileOutputStream fos = new FileOutputStream(target.toFile())) {
            new ReliableUdpReceiver(socket, server, fos).receiveStream();
        }

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("✅ Получено: " + IoUtils.formatTransferRate(size, elapsed));
        System.out.println("Файл сохранён: " + target.getFileName());

        // Фаза 3: финальное сообщение
        String finalMsg = UdpIo.receiveLine(socket);
        System.out.println("Сервер: " + finalMsg);
    }
}