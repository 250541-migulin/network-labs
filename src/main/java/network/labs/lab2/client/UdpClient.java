package network.labs.lab2.client;

import network.labs.lab2.common.Config;
import network.labs.lab2.common.UdpPacket;
import network.labs.lab2.transport.ReliableReceiver;
import network.labs.lab2.transport.ReliableSender;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Scanner;

public final class UdpClient {
    private final DatagramSocket sock;
    private final InetSocketAddress server;
    private final byte[] buf = new byte[1536];

    public UdpClient(String host, int port) throws IOException {
        sock = new DatagramSocket();
        sock.setReceiveBufferSize(Config.SOCK_BUF_SIZE);
        sock.setSendBufferSize(Config.SOCK_BUF_SIZE);
        sock.setSoTimeout(2000);
        server = new InetSocketAddress(host, port);
    }

    public void start() throws IOException {
        Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8);
        System.out.println("Команды: ECHO, TIME, CLOSE, UPLOAD <файл>, DOWNLOAD <файл>");
        while (true) {
            System.out.print("> ");
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;
            if (handle(line)) break;
        }
    }

    private boolean handle(String cmd) throws IOException {
        String[] parts = cmd.split("\\s+", 2);
        String action = parts[0].toUpperCase();
        send(cmd);

        if ("UPLOAD".equals(action) && parts.length > 1) return upload(parts[1]);
        if ("DOWNLOAD".equals(action) && parts.length > 1) return download(parts[1]);

        String resp = receiveCmd();
        System.out.println(resp.startsWith("ERROR:") ? "❌ " + resp : "Сервер: " + resp);
        return "CLOSE".equals(action);
    }

    private boolean upload(String fname) throws IOException {
        String ok = receiveCmd();
        if (!ok.startsWith("OK ")) { System.out.println("❌ " + ok); return false; }
        long offset = Long.parseLong(ok.substring(3).trim());
        Path src = Config.SRC_DIR.resolve(fname);
        long rem = Files.size(src) - offset;
        if (rem <= 0) { System.out.println("✅ Файл уже загружен"); return false; }

        send(String.valueOf(rem));
        if (!"READY".equals(receiveCmd())) { System.out.println("❌ Ожидается READY"); return false; }

        System.out.printf("📤 Отправка: %s (%d байт)\n", fname, rem);
        long t0 = System.currentTimeMillis();
        try (var in = Files.newInputStream(src)) {
            in.skipNBytes(offset);
            new ReliableSender(sock, server).sendStream(in, rem);
        }
        System.out.println("Сервер: " + receiveCmd());
        System.out.printf("✅ %.1f Мбит/с\n", (rem * 8.0) / (System.currentTimeMillis() - t0));
        return false;
    }

    private boolean download(String fname) throws IOException {
        if (!"OK".equals(receiveCmd())) { System.out.println("❌ Ошибка сервера"); return false; }
        Path tgt = Config.DST_DIR.resolve("down_" + fname);
        long loc = Files.exists(tgt) ? Files.size(tgt) : 0;
        send(String.valueOf(loc));

        String sz = receiveCmd();
        long rem = Long.parseLong(sz.trim());
        if (rem <= 0) { System.out.println("✅ Файл уже актуален"); return false; }

        System.out.printf("📥 Приём: %s (%d байт)\n", fname, rem);
        long t0 = System.currentTimeMillis();
        try (var out = Files.newOutputStream(tgt, StandardOpenOption.CREATE,
                loc > 0 ? StandardOpenOption.APPEND : StandardOpenOption.WRITE)) {
            new ReliableReceiver(sock, server).receiveStream(out, rem);
        }
        System.out.println("Сервер: " + receiveCmd());
        System.out.printf("✅ %.1f Мбит/с | %s\n", (rem * 8.0) / (System.currentTimeMillis() - t0), tgt.getFileName());
        return false;
    }

    private void send(String text) throws IOException {
        byte[] b = UdpPacket.cmd(text).toBytes();
        sock.send(new DatagramPacket(b, b.length, server));
    }

    /** Ждёт ТОЛЬКО CMD. Игнорирует DATA/ACK/FIN. Блокирует поток эффективно. */
    private String receiveCmd() throws IOException {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            try {
                sock.setSoTimeout(200);
                DatagramPacket dp = new DatagramPacket(buf, buf.length);
                sock.receive(dp);
                UdpPacket p = UdpPacket.fromBytes(dp.getData(), dp.getLength());
                if (p.isCmd()) return new String(p.dataUnsafe(), 0, p.dataLength(), StandardCharsets.UTF_8).trim();
            } catch (SocketTimeoutException ignored) {}
        }
        return "таймаут";
    }

    public static void main(String[] args) throws IOException {
        new UdpClient(Config.SERVER_HOST, Config.PORT).start();
    }
}