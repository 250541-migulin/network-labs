package network.labs.lab3.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class TcpNioContext {
    private final SocketChannel channel;
    private final Path serverDir;
    private final ByteBuffer readBuf = ByteBuffer.allocate(64 * 1024);

    public TcpNioContext(SocketChannel channel, Path serverDir) {
        this.channel = channel;
        this.serverDir = serverDir;
    }

    public InetSocketAddress remote() throws IOException {
        return (InetSocketAddress) channel.getRemoteAddress();
    }

    public Path filesDir() {
        return serverDir;
    }

    public String readLine() throws IOException {
        readBuf.clear();
        int n = channel.read(readBuf);
        if (n == -1) return null;
        readBuf.flip();
        return StandardCharsets.UTF_8.decode(readBuf).toString().trim();
    }

    public void writeLine(String line) throws IOException {
        ByteBuffer out = StandardCharsets.UTF_8.encode(line + Protocol.CRLF);
        while (out.hasRemaining()) channel.write(out);
    }

    public void writeBytes(byte[] bytes, int off, int len) throws IOException {
        ByteBuffer out = ByteBuffer.wrap(bytes, off, len);
        while (out.hasRemaining()) channel.write(out);
    }

    public int chunkSize() {
        return Protocol.TCP_CHUNK_SIZE;
    }
}
