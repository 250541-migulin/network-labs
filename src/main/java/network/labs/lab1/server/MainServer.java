package network.labs.lab1.server;

public class MainServer {
    public static void main(String[] args) {
        TcpServer server = new TcpServer(8888);
        server.start();
    }
}