package network.labs.lab1.client;

import network.labs.lab1.common.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * Запуск TCP-клиента.
 */
public class MainClientTcp {
    private static final Logger log = LoggerFactory.getLogger(MainClientTcp.class);

    public static void main(String[] args) {

        try {
            log.info("Запуск клиента...");
            TcpClient client = new TcpClient();
            client.start();
        } catch (UnknownHostException e) {
            log.error("Хост не найден: {}", Config.TCP_SERVER_HOST, e);
            System.exit(1);
        }catch (SocketException e) {
            // Соединение закрыто сервером (FIN/RST)
            log.info("Соединение закрыто сервером: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Ошибка клиента", e);
            System.exit(1);
        }
    }
}