package network.labs.lab1.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Path;

/**
 * Контекст с поддержкой файловых операций.
 * Расширяет CommandContext.
 */
public interface FileAwareContext extends CommandContext {
    Path filesDir();
    InputStream inputStream() throws IOException;
    OutputStream outputStream() throws IOException;

    Socket getSocket();
    InetAddress getClientIp();
}