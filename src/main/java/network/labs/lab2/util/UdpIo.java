package network.labs.lab2.util;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public final class UdpIo {
    private UdpIo() {}

    private static final ThreadLocal<BlockingQueue<String>> lineBuffer =
            ThreadLocal.withInitial(LinkedBlockingQueue::new);

    public static void sendLine(DatagramSocket socket, InetSocketAddress peer, String line)
            throws IOException {
        byte[] data = (line + "\r\n").getBytes(StandardCharsets.UTF_8); // ← \r\n для совместимости
        DatagramPacket packet = new DatagramPacket(data, data.length, peer);
        socket.send(packet);
    }

    public static String receiveLine(DatagramSocket socket) throws IOException {
        BlockingQueue<String> buf = lineBuffer.get();
        String cached = buf.poll();
        if (cached != null) return cached;

        byte[] raw = new byte[1500];
        DatagramPacket packet = new DatagramPacket(raw, raw.length);
        socket.receive(packet);

        String content = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
        String[] lines = content.split("\n", -1);

        for (int i = 1; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (!trimmed.isEmpty()) {
                buf.offer(trimmed);
            }
        }

        String first = lines[0].trim();
        return first.isEmpty() ? receiveLine(socket) : first;
    }

    // 👇 НОВЫЙ МЕТОД — для сервера
    public static String receiveString(DatagramPacket packet) {
        return new String(
                packet.getData(), 0, packet.getLength(),
                StandardCharsets.UTF_8
        ).trim();
    }
}