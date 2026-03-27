package network.labs.lab2.client;

import network.labs.lab2.common.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketException;

/**
 * Запуск UDP-клиента.
 */
public class MainClientUdp {
    private static final Logger log = LoggerFactory.getLogger(MainClientUdp.class);

    public static void main(String[] args) {
        try {
            log.info("Запуск клиента...");
            UdpClient client = new UdpClient(Config.SERVER_HOST, Config.PORT);
            client.start();
        } catch (SocketException e) {
            // Соединение закрыто или проблема с сокетом
            log.info("Соединение закрыто: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Ошибка клиента", e);
            System.exit(1);
        }
    }
}