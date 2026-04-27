package network.labs.lab1.server;

import network.labs.lab1.common.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.BindException;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * Точка входа сервера ЛР-01.
 *
 * Запускает TcpServer в отдельном потоке. Основной поток блокируется на
 * server.start(), который принимает подключения и обрабатывает клиентов
 * последовательно (один за раз).
 *
 * Обработка ошибок:
 * - UnknownHostException: неверный адрес в конфиге — фатальная ошибка
 * - SocketException: проблемы с сокетом — логируем и выходим
 * - Другие исключения: полная трассировка в лог и аварийное завершение
 */
public class MainServerTcp {
    private static final Logger log = LoggerFactory.getLogger(MainServerTcp.class);

    public static void main(String[] args) {
        log.debug("main: вход в точку входа сервера");
        log.info("Запуск сервера...");

        try {
            log.debug("main: создание экземпляра TcpServer");
            TcpServer server = new TcpServer();

            log.debug("main: вызов server.start() — блокирующий режим");
            server.start(); // Блокирующий вызов

            log.debug("main: server.start() завершён (сервер остановлен)");

        } catch (Exception e) {
            log.error("Непредвиденная ошибка сервера", e);
            System.exit(1);
        }
    }
}