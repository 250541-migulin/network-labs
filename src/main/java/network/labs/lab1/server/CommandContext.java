package network.labs.lab1.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class CommandContext {
    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public CommandContext(Socket socket) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
    }

    public InputStream in() { return in; }
    public OutputStream out() { return out; }
    public Socket socket() { return socket; }
}
