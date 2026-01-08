package network.labs.lab3.server;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class UdpNioContext {
    private final DatagramChannel channel;
    private final SocketAddress clientAddr;
    private final Path serverDir;

    public UdpNioContext(DatagramChannel channel, SocketAddress clientAddr, Path serverDir) {
        this.channel = channel;
        this.clientAddr = clientAddr;
        this.serverDir = serverDir;
    }

    public SocketAddress remote() {
        return clientAddr;
    }

    public Path filesDir() {
        return serverDir;
    }

    // Отправка текстовой строки
    public void sendLine(String line) throws IOException {
        ByteBuffer out = StandardCharsets.UTF_8.encode(line + Protocol.CRLF);
        channel.send(out, clientAddr);
    }

    // Отправка бинарного блока
    public void sendChunk(byte[] bytes, int off, int len) throws IOException {
        ByteBuffer out = ByteBuffer.wrap(bytes, off, len);
        channel.send(out, clientAddr);
    }

    // Приём строки
    public String receiveLine() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(Protocol.UDP_CHUNK_SIZE);
        SocketAddress addr = channel.receive(buf);
        buf.flip();
        return StandardCharsets.UTF_8.decode(buf).toString().trim();
    }

    // Приём бинарного блока
    public byte[] receiveChunk() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(Protocol.UDP_CHUNK_SIZE);
        SocketAddress addr = channel.receive(buf);
        buf.flip();
        byte[] data = new byte[buf.remaining()];
        buf.get(data);
        return data;
    }

    public int chunkSize() {
        return Protocol.UDP_CHUNK_SIZE;
    }
}
