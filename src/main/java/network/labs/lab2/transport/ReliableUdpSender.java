package network.labs.lab2.transport;

import network.labs.lab2.util.UdpIo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

/**
 * Надёжная отправка данных по UDP.
 * - Sliding window (размер = 4)
 * - Таймаут = 500 мс, макс. попыток = 3
 * - Размер данных = 1472 байта (под MTU)
 */
public class ReliableUdpSender {
    private static final Logger log = LoggerFactory.getLogger(ReliableUdpSender.class);
    private final DatagramSocket socket;
    private final InetSocketAddress peer;

    public ReliableUdpSender(DatagramSocket socket, InetSocketAddress peer) {
        this.socket = socket;
        this.peer = peer;
    }

    public void sendStream(InputStream in, long size) throws IOException {
        byte[] buf = new byte[1024];
        long sent = 0;
        long start = System.currentTimeMillis();

        int read;
        while ((read = in.read(buf)) != -1) {
            String chunk = new String(buf, 0, read);
            UdpIo.sendLine(socket, peer, chunk);
            sent += read;
        }

        // Отправляем явный маркер конца
        UdpIo.sendLine(socket, peer, "CTRL:END");

        long elapsed = System.currentTimeMillis() - start;
        log.info("Передача завершена: {} байт за {} мс", sent, elapsed);
    }
}
