package network.labs.lab2.transport;

import network.labs.lab2.util.UdpIo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
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

    public void receiveStream() throws IOException {
        long received = 0;
        long start = System.currentTimeMillis();

        while (true) {
            String line = UdpIo.receiveLine(socket);

            // Явный маркер конца передачи
            if ("CTRL:END".equals(line)) {
                log.debug("Получен CTRL:END — конец передачи");
                break;
            }

            byte[] data = line.getBytes();
            out.write(data);
            received += data.length;
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("Приём завершён: {} байт за {} мс", received, elapsed);
    }
}
