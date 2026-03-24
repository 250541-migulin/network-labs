package network.labs.lab1.client;

import network.labs.lab1.common.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * Запуск TCP-клиента (ЛР-01).
 * Все настройки берутся из Config.java.
 */
public class MainClientTcp {
    private static final Logger log = LoggerFactory.getLogger(MainClientTcp.class);

    public static void main(String[] args) {
        try {
            // Все настройки из конфига — никаких аргументов
            TcpClient client = new TcpClient(Config.TCP_SERVER_HOST, Config.TCP_SERVER_PORT);
            client.start(); // Блокирующий вызов

        } catch (UnknownHostException e) {
            log.error("Хост не найден: {}", Config.TCP_SERVER_HOST, e);
            System.exit(1);

        } catch (SocketTimeoutException e) {
            log.error("Таймаут соединения ({} мс)", Config.CLIENT_SOCKET_TIMEOUT_MS, e);
            System.exit(1);

        } catch (SocketException e) {
            log.error("Ошибка сети", e);
            System.exit(1);

        } catch (IOException e) {
            log.error("Ошибка ввода-вывода", e);
            System.exit(1);

        } catch (Exception e) {
            log.error("Неожиданная ошибка", e);
            System.exit(1);
        }
    }
}