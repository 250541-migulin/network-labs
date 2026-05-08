package network.labs.lab2.common;

/**
 * Утилиты для сетевых расчётов.
 * Все методы статические, класс не создаётся в экземплярах.
 */
public final class NetworkUtils {
    private NetworkUtils() {}

    /**
     * Рассчитывает скорость передачи данных в Мбит/с.
     *
     * Формула: (байты × 8) / (миллисекунды × 1000)
     *
     * @param bytes     количество переданных/принятых полезных байт
     * @param elapsedMs время передачи в миллисекундах
     * @return скорость в Мбит/с (возвращает 0.0, если время <= 0)
     */
    public static double calcSpeedMbps(long bytes, long elapsedMs) {
        if (elapsedMs <= 0) return 0.0;
        return (bytes * 8.0) / (elapsedMs * 1000.0);
    }
}