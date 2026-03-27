package network.labs.lab2.server;

import network.labs.lab2.common.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Точка входа сервера ЛР-02 (UDP).
 *
 * Запускает UdpServer с параметрами из аргументов или по умолчанию.
 */
public class MainServerUdp {

    private static final Logger log = LoggerFactory.getLogger(MainServerUdp.class);

    /**
     * Запуск сервера.
     *
     * Аргументы командной строки:
     * - [0] порт (по умолчанию: 8888)
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        log.debug("main: вход в точку входа сервера");
        log.info("Запуск UDP-сервера...");

        try {
            // Парсинг аргументов
            int port = args.length > 0 ? Integer.parseInt(args[0]) : 8888;

            log.debug("main: порт={}", port);

            // Запуск сервера
            log.debug("main: создание экземпляра UdpServer");
            UdpServer server = new UdpServer(port);

            log.debug("main: вызов server.start() — блокирующий режим");
            server.start();

            log.debug("main: сервер завершил работу");

        } catch (NumberFormatException e) {
            log.error("Неверный формат порта: '{}'",
                    args.length > 0 ? args[0] : "8888", e);
            System.exit(1);

        } catch (Exception e) {
            log.error("Ошибка сервера", e);
            System.exit(1);
        }

        log.debug("main: выход из main()");
    }
}