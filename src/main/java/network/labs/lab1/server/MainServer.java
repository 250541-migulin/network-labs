package network.labs.lab1.server;

import java.nio.file.Path;

/**
 * Точка входа для запуска TCP‑сервера.
 * Аргументы командной строки:
 *  args[0] - порт (по умолчанию 8888)
 */
public class MainServer {
    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8888;
        new TcpServer(port).start();
    }
}
