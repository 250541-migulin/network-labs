package network.labs.lab1.server;

/**
 * Запуск TCP-сервера.
 * Аргумент: [port]
 */
public class MainServerTcp {
    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8888;
        new TcpServer(port).start();
    }
}