package network.labs.lab1.server;

import network.labs.lab1.common.IoUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Контекст выполнения команды на сервере.
 *
 * @param socket сокет клиента
 * @param in     поток ввода от клиента
 * @param out    поток вывода к клиенту
 */
public record ServerCommandContext(Socket socket, InputStream in, OutputStream out) {
    public void writeLine(String line) throws java.io.IOException {
        IoUtils.writeLine(out, line);
    }
}
