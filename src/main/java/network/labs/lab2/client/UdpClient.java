package network.labs.lab2.client;

import network.labs.lab2.core.UdpCommandRegistry;
import network.labs.lab2.client.commands.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class UdpClient {
    private static final Logger log = LoggerFactory.getLogger(UdpClient.class);
    private final String host;
    private final int port;

    public UdpClient(String host, int port) { this.host = host; this.port = port; }

    public void start() {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress addr = InetAddress.getByName(host);
            InetSocketAddress serverAddr = new InetSocketAddress(addr, port);
            log.info("Готов к работе с UDP {}:{}", host, port);

            UdpCommandRegistry registry = ClientCommandsFactory.create();

            Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
            while (true) {
                System.out.print("> ");
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                try {
                    registry.dispatch(line, socket, serverAddr);
                } catch (IOException e) {
                    log.error("Ошибка UDP", e);
                    System.err.println("Ошибка: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Клиент завершён", e);
        }
    }
}
