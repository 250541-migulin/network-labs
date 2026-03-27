package network.labs.lab2.common;

/**
 * Утилиты для форматирования (без сетевой логики).
 */
public class IoUtils {

    /**
     * Форматирует размер в байтах в человекочитаемый вид.
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)) + " MB";
        return (bytes / (1024 * 1024 * 1024)) + " GB";
    }

    /**
     * Рассчитывает битрейт в Мбит/с.
     */
    public static String formatBitrate(long bytes, long elapsedMs) {
        if (elapsedMs <= 0) return "0 Мбит/с";
        double mbps = (bytes * 8.0 * 1000) / (elapsedMs * 1_000_000);
        return String.format("%.1f Мбит/с", mbps);
    }
}