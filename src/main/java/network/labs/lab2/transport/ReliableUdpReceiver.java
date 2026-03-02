package network.labs.lab2.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

public class ReliableUdpReceiver {
    private static final Logger log = LoggerFactory.getLogger(ReliableUdpReceiver.class);
    private final DatagramSocket socket;
    private final InetSocketAddress peer;
    private final OutputStream out;

    public ReliableUdpReceiver(DatagramSocket socket, InetSocketAddress peer, OutputStream out) {
        this.socket = socket;
        this.peer = peer;
        this.out = out;
    }

    public void receiveStream(long expectedSize) throws IOException {
        int originalTimeout = socket.getSoTimeout();
        try {
            socket.setSoTimeout(5000); // 5 секунд на ожидание пакета

            int expectedSeq = 0;
            long receivedBytes = 0;
            int lastPercent = -1;

            while (receivedBytes < expectedSize) {
                byte[] buf = new byte[1500];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                if (!packet.getAddress().equals(peer.getAddress()) ||
                        packet.getPort() != peer.getPort()) {
                    continue;
                }

                DataPacket dataPkt = DataPacket.fromBytes(packet.getData());
                log.debug("Получен пакет seq={}", dataPkt.seqNum);

                // Отправляем ACK
                AckPacket ack = new AckPacket(dataPkt.seqNum);
                byte[] ackRaw = ack.toBytes();
                DatagramPacket ackPkt = new DatagramPacket(ackRaw, ackRaw.length, peer);
                socket.send(ackPkt);

                if (dataPkt.seqNum == expectedSeq) {
                    out.write(dataPkt.data);
                    receivedBytes += dataPkt.data.length;
                    expectedSeq++;

                    // Прогресс-бар (относительно оставшегося объёма)
                    int percent = (int) (receivedBytes * 100 / expectedSize);
                    if (percent != lastPercent && percent % 10 == 0) {
                        System.out.println("Докачка: " + percent + "% (" + receivedBytes + " из " + expectedSize + " байт)");
                        lastPercent = percent;
                    }
                }
            }
            log.info("Приём завершён: {} байт", receivedBytes);
        } finally {
            socket.setSoTimeout(originalTimeout);
        }
    }
}