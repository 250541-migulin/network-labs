package network.labs.lab1.server;

import network.labs.lab1.common.FileAwareContext;
import network.labs.lab1.common.IoUtils;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Path;

public class TcpServerCommandContext implements FileAwareContext {
    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final Path serverDir;
    private final InetAddress clientIp;

    public TcpServerCommandContext(Socket socket, Path serverDir, InetAddress clientIp) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
        this.serverDir = serverDir;
        this.clientIp = clientIp;
    }

    @Override
    public void writeLine(String line) throws IOException {
        IoUtils.writeLine(out, line);
    }

    @Override
    public String readLine() throws IOException {
        return IoUtils.readLine(in);
    }

    @Override
    public Path filesDir() {
        return serverDir;
    }

    @Override
    public InputStream inputStream() {
        return in;
    }

    @Override
    public OutputStream outputStream() {
        return out;
    }

    @Override
    public Socket getSocket() {
        return socket;
    }

    @Override
    public InetAddress getClientIp() {
        return clientIp;
    }
}