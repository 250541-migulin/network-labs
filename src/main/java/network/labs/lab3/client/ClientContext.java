package network.labs.lab3.client;

import network.labs.lab3.server.Protocol;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class ClientContext {
    private final SocketChannel channel;

    public ClientContext(SocketChannel channel) {
        this.channel = channel;
    }

    public static ClientContext connect(String host, int port) throws IOException {
        SocketChannel ch = SocketChannel.open(new InetSocketAddress(host, port));
        return new ClientContext(ch);
    }

    public void sendLine(String line) throws IOException {
        ByteBuffer out = StandardCharsets.UTF_8.encode(line + Protocol.CRLF);
        while (out.hasRemaining()) channel.write(out);
    }

    public String readLine() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(8192);
        int n = channel.read(buf);
        if (n <= 0) return null;
        buf.flip();
        return StandardCharsets.UTF_8.decode(buf).toString().trim();
    }

    public void close() throws IOException {
        channel.close();
    }
}
