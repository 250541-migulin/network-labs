package network.labs.lab2.server;

import network.labs.lab2.core.UdpCommandRegistry;
import network.labs.lab2.util.PathsConfig;
import network.labs.lab2.util.UdpIo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.nio.file.Path;

public class UdpServer {
    private static final Logger log = LoggerFactory.getLogger(UdpServer.class);
    private final int port;
    private final Path serverDir;

    public UdpServer(int port) {
        this.port = port;
        this.serverDir = PathsConfig.SERVER_UDP;
    }

    public void start() {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            log.info("📡 UDP-сервер запущен на порту {}", port);

            UdpCommandRegistry registry = ServerCommandsFactory.create(serverDir);

            while (true) {
                try {
                    byte[] buf = new byte[1500];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);

                    String line = UdpIo.receiveString(packet); // ← работает
                    InetSocketAddress peer = new InetSocketAddress(
                            packet.getAddress(), packet.getPort()
                    );
                    log.info("Получено от {}: {}", peer, line);

                    registry.dispatch(line, socket, peer);

                } catch (Exception e) {
                    log.error("Ошибка обработки", e);
                }
            }
        } catch (IOException e) {
            log.error("Сервер остановлен", e);
        }
    }
}