package network.labs.lab1.client;

import network.labs.lab1.common.CommandContext;
import network.labs.lab1.common.FileAwareContext;
import network.labs.lab1.common.IoUtils;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Path;

public class TcpClientCommandContext implements FileAwareContext {
    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final Path clientDir;

    public TcpClientCommandContext(Socket socket, Path clientDir) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
        this.clientDir = clientDir;
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
        return clientDir;
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
        // На клиенте "client IP" — это localhost или IP сервера?
        // Но для совместимости вернём локальный адрес сокета
        return socket.getLocalAddress();
    }
}