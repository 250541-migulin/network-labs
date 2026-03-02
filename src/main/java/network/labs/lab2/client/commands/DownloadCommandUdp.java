package network.labs.lab2.client.commands;

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
import java.nio.file.Paths;

public class DownloadCommandUdp implements UdpCommand {
    private static final Logger log = LoggerFactory.getLogger(DownloadCommandUdp.class);
    private static final Path CLIENT_DIR = Paths.get("files", "client_udp");

    @Override
    public String name() { return "DOWNLOAD"; }

    @Override
    public void execute(String[] args, DatagramSocket socket, InetSocketAddress server) throws IOException {
        if (args.length < 1) {
            System.err.println("Использование: DOWNLOAD <имя_файла>");
            return;
        }

        String filename = args[0];
        Path target = CLIENT_DIR.resolve("downloaded_" + filename);
        Files.createDirectories(CLIENT_DIR);

        long localSize = 0;
        boolean resume = false;
        if (Files.exists(target)) {
            localSize = Files.size(target);
            System.out.println("Найден локальный файл (" + localSize + " байт). Запрашиваем докачку...");
            resume = true;
        }

        // Отправляем запрос
        String request = resume ? "DOWNLOAD " + filename + " " + localSize : "DOWNLOAD " + filename;
        UdpIo.sendLine(socket, server, request);

        // Получаем ответ
        String okLine = UdpIo.receiveLine(socket);
        if (!okLine.startsWith("OK ")) {
            System.err.println("Ошибка сервера: " + okLine);
            return;
        }

        long fileSize = Long.parseLong(okLine.substring(3).trim());
        long remaining = fileSize - localSize;

        UdpIo.receiveLine(socket); // READY

        if (remaining <= 0) {
            System.out.println("Файл уже полный.");
            return;
        }

        System.out.println("Докачка " + remaining + " байт...");

        long start = System.currentTimeMillis();
        try (FileOutputStream fos = new FileOutputStream(target.toFile(), true)) {
            new ReliableUdpReceiver(socket, server, fos).receiveStream(remaining);
        }
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("Получено: " + IoUtils.formatTransferRate(remaining, elapsed));
        System.out.println("Файл сохранён: " + target.getFileName());
        System.out.println("Сервер: " + UdpIo.receiveLine(socket));
    }
}