package network.labs.lab2.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;

public class ReliableUdpSender {
    private static final Logger log = LoggerFactory.getLogger(ReliableUdpSender.class);
    private static final int MAX_PAYLOAD = 1472;
    private static final int WINDOW_SIZE = 4;
    private static final int TIMEOUT_MS = 500;
    private static final int MAX_RETRIES = 5; // ← ограничение попыток

    private final DatagramSocket socket;
    private final InetSocketAddress peer;

    public ReliableUdpSender(DatagramSocket socket, InetSocketAddress peer) {
        this.socket = socket;
        this.peer = peer;
    }

    public void sendStream(InputStream in, long totalSize) throws IOException {
        int originalTimeout = socket.getSoTimeout();
        try {
            socket.setSoTimeout(TIMEOUT_MS);

            Queue<DataPacket> window = new ArrayDeque<>();
            int baseSeq = 0;
            int nextSeq = 0;
            long sentBytes = 0;
            int retryCount = 0;

            byte[] buffer = new byte[MAX_PAYLOAD];

            while (sentBytes < totalSize || !window.isEmpty()) {
                // Отправляем новые пакеты
                while (nextSeq < baseSeq + WINDOW_SIZE && sentBytes < totalSize) {
                    int toRead = (int) Math.min(MAX_PAYLOAD, totalSize - sentBytes);
                    int read = in.read(buffer, 0, toRead);
                    if (read <= 0) break;

                    DataPacket pkt = new DataPacket(nextSeq, java.util.Arrays.copyOf(buffer, read));
                    sendPacket(pkt);
                    window.offer(pkt);
                    nextSeq++;
                    sentBytes += read;
                }

                try {
                    byte[] ackBuf = new byte[64];
                    DatagramPacket ackPkt = new DatagramPacket(ackBuf, ackBuf.length);
                    socket.receive(ackPkt);

                    if (!ackPkt.getAddress().equals(peer.getAddress()) ||
                            ackPkt.getPort() != peer.getPort()) {
                        continue;
                    }

                    AckPacket ack = AckPacket.fromBytes(ackPkt.getData());
                    while (!window.isEmpty() && window.peek().seqNum <= ack.ackNum) {
                        window.poll();
                        baseSeq = ack.ackNum + 1;
                    }
                    retryCount = 0; // сброс при успешном ACK

                } catch (java.net.SocketTimeoutException e) {
                    retryCount++;
                    if (retryCount > MAX_RETRIES) {
                        throw new IOException("Передача прервана: превышено количество попыток (" + MAX_RETRIES + ")");
                    }
                    log.warn("Таймаут ACK (попытка {}), повторная отправка {} пакетов", retryCount, window.size());
                    for (DataPacket pkt : window) {
                        sendPacket(pkt);
                    }
                }
            }
            log.info("Передача завершена: {} байт", totalSize);
        } finally {
            socket.setSoTimeout(originalTimeout);
        }
    }

    private void sendPacket(DataPacket pkt) throws IOException {
        byte[] raw = pkt.toBytes();
        DatagramPacket dp = new DatagramPacket(raw, raw.length, peer);
        socket.send(dp);
    }
}