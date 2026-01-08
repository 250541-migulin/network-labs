package network.labs.lab3.client;

import network.labs.lab3.server.Protocol;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;

public class UdpClientContext {
    private final DatagramChannel channel;
    private final SocketAddress serverAddr;

    public UdpClientContext(DatagramChannel channel, SocketAddress serverAddr) {
        this.channel = channel;
        this.serverAddr = serverAddr;
    }

    public static UdpClientContext connect(String host, int port) throws IOException {
        DatagramChannel ch = DatagramChannel.open();
        ch.configureBlocking(true); // блокирующий режим для простоты
        return new UdpClientContext(ch, new InetSocketAddress(host, port));
    }

    // Отправка строки
    public void sendLine(String line) throws IOException {
        ByteBuffer out = StandardCharsets.UTF_8.encode(line + Protocol.CRLF);
        channel.send(out, serverAddr);
    }

    // Приём строки
    public String readLine() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(8192);
        SocketAddress src = channel.receive(buf);
        if (src == null) return null;
        buf.flip();
        return StandardCharsets.UTF_8.decode(buf).toString().trim();
    }

    // Отправка бинарного блока
    public void sendChunk(byte[] bytes, int off, int len) throws IOException {
        ByteBuffer out = ByteBuffer.wrap(bytes, off, len);
        channel.send(out, serverAddr);
    }

    // Приём бинарного блока
    public byte[] readChunk() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(Protocol.UDP_CHUNK_SIZE);
        SocketAddress src = channel.receive(buf);
        if (src == null) return null;
        buf.flip();
        byte[] data = new byte[buf.remaining()];
        buf.get(data);
        return data;
    }

    public int chunkSize() {
        return Protocol.UDP_CHUNK_SIZE;
    }

    public void close() throws IOException {
        channel.close();
    }
}
