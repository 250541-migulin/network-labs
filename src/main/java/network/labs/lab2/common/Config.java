package network.labs.lab2.common;

import java.nio.file.Path;

/**
 * Конфигурация ЛР-2. Пути — абсолютные, для работы в GNS3.
 */
public final class Config {
    public static final int PORT = 8888;
    public static final String SERVER_HOST = "10.0.0.3"; // IP сервера

    // Заголовок нашего протокола: 7 байт
    public static final int HEADER_SIZE = 7;
    // MTU 1500 - 20(IP) - 8(UDP) - 7(наш) = 1465 байт полезной нагрузки
    public static final int PAYLOAD_SIZE = 1465;

    // Надёжный UDP: окно, таймауты, ретрансмиссии
    public static final int WINDOW_SIZE = 64;
    public static final int ACK_TIMEOUT_MS = 50;
    public static final int MAX_RETRIES = 10;
    public static final int SOCKET_TIMEOUT_MS = 5;

    // Буферы сокетов для высокой пропускной способности
    public static final int SOCK_BUF_SIZE = 256 * 1024;

    public static final Path SRC_DIR = Path.of("/root/files/source");
    public static final Path DST_DIR = Path.of("/root/files/tmp");

    private Config() {}
}