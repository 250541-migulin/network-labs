package network.labs.lab3.common;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

/**
 * Глобальная конфигурация для Лабораторной работы №3.
 * Содержит сетевые параметры, пути к файлам и настройки производительности.
 */
public final class Config {
    private Config() {}

    // Сетевые параметры
    public static final String SERVER_HOST = "10.0.0.3";
    public static final int SERVER_PORT = 8888;

    // Таймаут селектора в миллисекундах.
    // 1000 мс обеспечивает баланс между отзывчивостью сервера и нагрузкой на CPU.
    public static final int SELECT_TIMEOUT_MS = 1000;

    // Размер буфера для сетевого и файлового ввода-вывода.
    // Увеличен до 64 КБ для снижения количества системных вызовов и повышения пропускной способности.
    public static final int BUFFER_SIZE = 65536;

    // Разделитель строк в текстовом протоколе
    public static final String LINE_END = "\r\n";

    // Пути к директориям с файлами
    public static final Path SOURCE_DIR = Path.of("/root/files/source");
    public static final Path TMP_DIR = Path.of("/root/files/tmp");

    // Формат времени для команды TIME
    public static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
}