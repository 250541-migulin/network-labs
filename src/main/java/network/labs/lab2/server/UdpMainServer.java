package network.labs.lab2.server;

public class UdpMainServer {
    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8889;
        new UdpServer(port).start();
    }
}
