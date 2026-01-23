package network.labs.lab4;

import network.labs.lab1.server.TcpServerCommandContext;

import java.net.Socket;
import java.nio.file.Path;

public class ClientHandler implements Runnable {
    private final Socket client;
    private final Path serverDir;

    public ClientHandler(Socket client, Path serverDir) {
        this.client = client;
        this.serverDir = serverDir;
    }

    @Override
    public void run() {
        try {
           // TcpServerCommandContext ctx = new TcpServerCommandContext(client, serverDir);
            // та же логика, что в handleClient
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
