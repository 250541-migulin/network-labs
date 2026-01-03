package network.labs.lab1.client;

/**
 * Точка входа для запуска TCP‑клиента.
 * Аргументы командной строки:
 *  args[0] - хост (по умолчанию localhost)
 *  args[1] - порт (по умолчанию 8888)
 */
public class MainClient {
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8888;
        new TcpClient(host, port).start();
    }
}
