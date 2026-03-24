package network.labs.lab1.server;

import network.labs.lab1.common.Config;
import network.labs.lab1.common.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.BindException;

public class MainServerTcp {
    private static final Logger log = LoggerFactory.getLogger(MainServerTcp.class);

    public static void main(String[] args) {
        try {
            // 1. Очистка временной директории
            FileUtils.prepareDirectory(Config.TMP_DIR);

            TcpServer server = new TcpServer();
            // 2. Запуск сервера
            log.info("Запускаю сервер на порту {}", Config.TCP_SERVER_PORT);
            server.start();

        }  catch (Exception e) {
            log.error("Неожиданная ошибка: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}