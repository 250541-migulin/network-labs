package network.labs.lab2.server;

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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class UdpServer {
    private final DatagramSocket sock;
    private InetSocketAddress lastClient;
    private String lastFile;
    private final byte[] buf = new byte[1536];

    public UdpServer(int port) throws IOException {
        sock = new DatagramSocket(port);
        sock.setReceiveBufferSize(Config.SOCK_BUF_SIZE);
        sock.setSendBufferSize(Config.SOCK_BUF_SIZE);
        sock.setSoTimeout(50);
        System.out.println("✅ Сервер UDP запущен на :" + port);
    }

    public void start() throws IOException {
        while (true) {
            try {
                DatagramPacket dp = new DatagramPacket(buf, buf.length);
                sock.receive(dp);
                InetSocketAddress peer = new InetSocketAddress(dp.getAddress(), dp.getPort());
                UdpPacket pkt = UdpPacket.fromBytes(dp.getData(), dp.getLength());
                if (pkt.isCmd()) handleCmd(peer, pkt);
            } catch (SocketTimeoutException ignored) {}
        }
    }

    private void handleCmd(InetSocketAddress peer, UdpPacket pkt) throws IOException {
        String raw = new String(pkt.dataUnsafe(), 0, pkt.dataLength(), StandardCharsets.UTF_8).trim();
        String[] parts = raw.split("\\s+", 2);
        String cmd = parts[0].toUpperCase();

        switch (cmd) {
            case "ECHO" -> reply(peer, "Эхо: " + (parts.length > 1 ? parts[1] : ""));
            case "TIME" -> reply(peer, "Время: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            case "CLOSE" -> { lastClient = null; lastFile = null; reply(peer, "Сессия закрыта"); }
            case "UPLOAD" -> handleUpload(peer, parts);
            case "DOWNLOAD" -> handleDownload(peer, parts);
            default -> reply(peer, "ERROR: Неизвестная команда: " + cmd);
        }
    }

    private void reply(InetSocketAddress peer, String text) throws IOException {
        byte[] b = UdpPacket.cmd(text).toBytes();
        sock.send(new DatagramPacket(b, b.length, peer));
    }

    private void handleUpload(InetSocketAddress peer, String[] parts) throws IOException {
        if (parts.length < 2) { reply(peer, "ERROR: имя файла"); return; }
        String fname = parts[1];
        Path tgt = Config.DST_DIR.resolve(fname);
        long off = (peer.equals(lastClient) && fname.equals(lastFile) && Files.exists(tgt)) ? Files.size(tgt) : 0;
        reply(peer, "OK " + off);

        String sz = readCmd(peer);
        if (sz == null) return;
        long rem = Long.parseLong(sz.trim());
        if (rem <= 0) { reply(peer, "Файл уже загружен"); return; }

        reply(peer, "READY");
        System.out.println("📥 Приём: " + fname + " (" + rem + " байт)");
        long t0 = System.currentTimeMillis();
        try (var out = Files.newOutputStream(tgt, StandardOpenOption.CREATE, off > 0 ? StandardOpenOption.APPEND : StandardOpenOption.WRITE)) {
            new ReliableReceiver(sock, peer).receiveStream(out, rem);
        }
        System.out.printf("✅ Принято: %.1f Мбит/с\n", (rem * 8.0) / (System.currentTimeMillis() - t0));
        reply(peer, "Файл загружен: " + fname);
        lastClient = peer; lastFile = fname;
    }

    private void handleDownload(InetSocketAddress peer, String[] parts) throws IOException {
        if (parts.length < 2) { reply(peer, "ERROR: имя файла"); return; }
        String fname = parts[1];
        Path src = Config.SRC_DIR.resolve(fname);
        if (!Files.exists(src)) { reply(peer, "ERROR: файл не найден"); return; }

        reply(peer, "OK");
        String off = readCmd(peer);
        long skip = off != null ? Long.parseLong(off.trim()) : 0;
        long rem = Files.size(src) - skip;
        reply(peer, String.valueOf(rem));
        if (rem <= 0) { reply(peer, "Файл уже актуален"); return; }

        System.out.println("📤 Отправка: " + fname + " (" + rem + " байт)");
        long t0 = System.currentTimeMillis();
        try (var in = Files.newInputStream(src)) {
            in.skipNBytes(skip);
            new ReliableSender(sock, peer).sendStream(in, rem);
        }
        System.out.printf("✅ Отправлено: %.1f Мбит/с\n", (rem * 8.0) / (System.currentTimeMillis() - t0));
        reply(peer, "Файл отправлен: " + fname);
    }

    private String readCmd(InetSocketAddress expected) throws IOException {
        int old = sock.getSoTimeout();
        sock.setSoTimeout(2000);
        try {
            long deadline = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < deadline) {
                try {
                    sock.setSoTimeout(200);
                    DatagramPacket dp = new DatagramPacket(buf, buf.length);
                    sock.receive(dp);
                    if (!dp.getAddress().equals(expected.getAddress())) continue;
                    UdpPacket p = UdpPacket.fromBytes(dp.getData(), dp.getLength());
                    if (p.isCmd()) return new String(p.dataUnsafe(), 0, p.dataLength(), StandardCharsets.UTF_8).trim();
                } catch (SocketTimeoutException ignored) {}
            }
        } finally { sock.setSoTimeout(old); }
        return null;
    }
}