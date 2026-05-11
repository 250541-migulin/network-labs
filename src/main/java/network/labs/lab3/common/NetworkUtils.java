package network.labs.lab3.common;

/**
 * Утилиты для сетевых расчётов.
 */
public final class NetworkUtils {
    private NetworkUtils() {}

    /**
     * Рассчитывает скорость в Мбит/с.
     */
    public static double calcSpeedMbps(long bytes, long elapsedMs) {
        if (elapsedMs <= 0) return 0.0;
        return (bytes * 8.0) / (elapsedMs * 1000.0);
    }
}