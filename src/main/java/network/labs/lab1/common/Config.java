// Config.java
package network.labs.lab1.common;

import java.nio.file.Path;

/**
 * Единая конфигурация для ЛР-01.
 * Клиент и сервер используют одни и те же настройки.
 */
public class Config {
    // === Сетевые настройки ===
    public static final String TCP_SERVER_HOST = "10.0.0.2";  // IP сервера в GNS3
    public static final int TCP_SERVER_PORT = 8888;

    // === Таймауты (одинаковые для клиента и сервера) ===
    public static final int CLIENT_SOCKET_TIMEOUT_MS = 120_000; // 2 минуты

    // === Пути к директориям (одинаковые на клиенте и сервере) ===
    // source/ — файлы для ОТПРАВКИ (НЕ очищается)
    public static final Path SOURCE_DIR = Path.of("files/source");
    // tmp/ — файлы для ПРИЁМА (очищается при старте)
    public static final Path TMP_DIR = Path.of("files/tmp");

    // Базовая директория (для удобства)
    public static final Path BASE_DIR = Path.of("files");
}