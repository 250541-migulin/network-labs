package network.labs.lab1.common;

import java.nio.file.Path;

/**
 * Единая конфигурация для ЛР-01.
 */
public class Config {
    // Сеть
    public static final String TCP_SERVER_HOST = "10.0.0.2";
    public static final int TCP_SERVER_PORT = 8888;

    // Таймауты (2 минуты)
    public static final int SOCKET_TIMEOUT_MS = 120_000;

    // Пути (одинаковые на клиенте и сервере)
    public static final Path SOURCE_DIR = Path.of("/root/files/source");
    public static final Path TMP_DIR = Path.of("/root/files/tmp");
}