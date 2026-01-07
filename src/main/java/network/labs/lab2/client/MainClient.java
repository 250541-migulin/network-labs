package network.labs.lab2.client;

public class MainClient {
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8889; // ← 8889!
        new UdpClient(host, port).start();
    }
}
