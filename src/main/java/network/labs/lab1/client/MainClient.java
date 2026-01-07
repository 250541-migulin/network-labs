package network.labs.lab1.client;

/**
 * Запуск TCP-клиента.
 * Аргументы: [host] [port]
 */
public class MainClient {
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8888;
        new TcpClient(host, port).start();
    }
}