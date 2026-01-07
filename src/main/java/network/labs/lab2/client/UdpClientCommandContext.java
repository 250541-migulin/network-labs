package network.labs.lab2.client;

import network.labs.lab1.common.CommandContext;
import network.labs.lab1.common.FileAwareContext;
import network.labs.lab1.common.FileUtils;
import network.labs.lab2.transport.ReliableUdpSender;
import network.labs.lab2.transport.UdpPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Контекст UDP-клиента.
 * Совместим с lab1.client.commands.* через интерфейсы.
 */
public class UdpClientCommandContext implements FileAwareContext {
    private static final int TIMEOUT_MS = 3000;
    private static final Logger log = LoggerFactory.getLogger(UdpClientCommandContext.class);

    private final DatagramSocket socket;
    private final InetSocketAddress serverAddr;
    private final Path clientDir;
    private UdpPacket lastResponse;

    public UdpClientCommandContext(DatagramSocket socket, InetSocketAddress serverAddr, Path clientDir) {
        this.socket = socket;
        this.serverAddr = serverAddr;
        this.clientDir = clientDir;
        FileUtils.ensureDirectory(clientDir);
    }

    @Override
    public void writeLine(String line) throws IOException {
        byte[] data = (line + "\r\n").getBytes(StandardCharsets.UTF_8);
        UdpPacket packet = new UdpPacket(UdpPacket.Type.DATA, 0, data);
        byte[] raw = packet.toBytes();

        DatagramPacket dp = new DatagramPacket(raw, raw.length, serverAddr);
        socket.send(dp);
    }

    @Override
    public String readLine() throws IOException {
        byte[] buf = new byte[1500];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.setSoTimeout(TIMEOUT_MS);
        socket.receive(packet);

        UdpPacket p = UdpPacket.fromBytes(packet.getData(), packet.getLength());

        lastResponse = p;
        return p.getDataAsString().trim();
    }

    @Override
    public Path filesDir() {
        return clientDir;
    }

    @Override
    public InputStream inputStream() throws IOException {
        return new ByteArrayInputStream(new byte[0]); // не используется в клиенте при UPLOAD
    }

    @Override
    public OutputStream outputStream() throws IOException {
        return new OutputStream() {
            private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            @Override
            public void write(int b) throws IOException {
                buffer.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                buffer.write(b, off, len);
            }

            @Override
            public void flush() throws IOException {
                byte[] data = buffer.toByteArray();
                try (InputStream bis = new ByteArrayInputStream(data)) {
                    ReliableUdpSender sender = new ReliableUdpSender(socket, serverAddr);
                    sender.sendStream(bis, data.length);

                    CountDownLatch latch = new CountDownLatch(1);
                    new Thread(() -> {
                        try {
                            while (true) {
                                byte[] ackBuf = new byte[1500];
                                DatagramPacket ack = new DatagramPacket(ackBuf, ackBuf.length);
                                socket.receive(ack);
                                UdpPacket p = UdpPacket.fromBytes(ack.getData(), ack.getLength());
                                if (p.type == UdpPacket.Type.ACK) {
                                    if (p.seq >= (data.length + 1471) / 1472 - 1) {
                                        latch.countDown();
                                        break;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Ошибка ожидания ACK", e);
                        }
                    }).start();

                    if (!latch.await(5, TimeUnit.SECONDS)) {
                        throw new IOException("Таймаут ожидания подтверждения");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Отправка прервана", e);
                }
            }
        };
    }

    public DatagramSocket getSocket() {
        return socket;
    }

    public InetSocketAddress getServerAddress() {
        return serverAddr;
    }
}