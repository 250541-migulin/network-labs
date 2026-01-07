package network.labs.lab2.server;

import network.labs.lab1.common.CommandContext;
import network.labs.lab1.common.FileAwareContext;
import network.labs.lab1.common.FileUtils;
import network.labs.lab2.transport.ReliableUdpReceiver;
import network.labs.lab2.transport.UdpPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Контекст UDP-сервера.
 * Совместим с lab1.server.commands.* через интерфейсы.
 */
public class UdpServerCommandContext implements FileAwareContext {
    private static final Logger log = LoggerFactory.getLogger(UdpServerCommandContext.class);

    private final DatagramSocket socket;
    private final DatagramPacket request;
    private final Path serverDir;

    public UdpServerCommandContext(DatagramSocket socket, DatagramPacket request, Path serverDir) {
        this.socket = socket;
        this.request = request;
        this.serverDir = serverDir;
        FileUtils.ensureDirectory(serverDir);
    }

    @Override
    public void writeLine(String line) throws IOException {
        byte[] data = (line + "\r\n").getBytes(StandardCharsets.UTF_8);
        UdpPacket packet = new UdpPacket(UdpPacket.Type.DATA, 0, data);
        byte[] raw = packet.toBytes();

        DatagramPacket response = new DatagramPacket(
                raw, raw.length,
                request.getAddress(), request.getPort()
        );
        socket.send(response);
    }

    @Override
    public String readLine() throws IOException {
        byte[] buf = new byte[1500];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);
        return new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
    }

    @Override
    public Path filesDir() {
        return serverDir;
    }

    @Override
    public InputStream inputStream() throws IOException {
        return new InputStream() {
            private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            private boolean received = false;

            @Override
            public int read() throws IOException {
                if (!received) {
                    try {
                        InetSocketAddress clientAddr = getClientAddress();
                        ReliableUdpReceiver receiver = new ReliableUdpReceiver(socket, clientAddr, buffer);
                        receiver.receiveStream();
                        received = true;
                    } catch (Exception e) {
                        throw new IOException("Ошибка приёма файла", e);
                    }
                }
                byte[] data = buffer.toByteArray();
                return data.length > 0 ? data[0] : -1;
            }

            @Override
            public int available() throws IOException {
                return received ? buffer.size() : 0;
            }
        };
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
                    InetSocketAddress clientAddr = getClientAddress();
                    network.labs.lab2.transport.ReliableUdpSender sender =
                            new network.labs.lab2.transport.ReliableUdpSender(socket, clientAddr);
                    sender.sendStream(bis, data.length);

                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Отправка прервана", e);
                }
            }
        };
    }

    // ===== Геттеры для команд (UploadCommandUdp и т.д.) =====
    public DatagramSocket getSocket() {
        return socket;
    }

    public InetSocketAddress getClientAddress() {
        return new InetSocketAddress(request.getAddress(), request.getPort());
    }
}