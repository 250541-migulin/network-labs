package network.labs.lab1.client;

import network.labs.lab1.common.CommandContext;
import network.labs.lab1.common.FileAwareContext;
import network.labs.lab1.common.IoUtils;

import java.io.*;
import java.net.Socket;
import java.nio.file.Path;

/**
 * Контекст TCP-клиента.
 * Реализует оба интерфейса для совместимости с командами.
 */
public class TcpClientCommandContext implements FileAwareContext {
    private final InputStream in;
    private final OutputStream out;
    private final Path clientDir;

    public TcpClientCommandContext(Socket socket, Path clientDir) throws IOException {
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
}