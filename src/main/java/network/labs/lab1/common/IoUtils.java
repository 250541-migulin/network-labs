package network.labs.lab1.common;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class IoUtils {

    public static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b == '\r') {
                in.mark(1);
                int next = in.read();
                if (next != '\n') in.reset();
                break;
            }
            buf.write(b);
        }
        byte[] bytes = buf.toByteArray();
        return (bytes.length == 0 && b == -1) ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    public static void writeLine(OutputStream out, String line) throws IOException {
        out.write(line.getBytes(StandardCharsets.UTF_8));
        out.write('\r');
        out.write('\n');
        out.flush();
    }

    public static long copyStream(InputStream in, OutputStream out, long length) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while (total < length && (read = in.read(buffer, 0, (int) Math.min(buffer.length, length - total))) != -1) {
            out.write(buffer, 0, read);
            total += read;
        }
        out.flush();
        return total;
    }

    /**
     * Форматирует скорость передачи данных.
     *
     * @param bytes      количество переданных байт
     * @param elapsedMs  время в миллисекундах (может быть дробным, например 0.45)
     * @return строка с описанием скорости, например "2.50 MB/s" или "очень быстро"
     */
    public static String formatTransferRate(long bytes, double elapsedMs) {
        // Защита от деления на ноль
        if (elapsedMs <= 0.0) {
            return "очень быстро";
        }

        double bytesPerSec = bytes / (elapsedMs / 1000.0);

        // Максимальная реалистичная скорость: 1 ГБ/с
        final double MAX_REALISTIC_BPS = 1_073_741_824.0; // 1 GiB/s

        if (bytesPerSec > MAX_REALISTIC_BPS) {
            return "очень быстро";
        }

        if (bytesPerSec < 1024) {
            return String.format("%.2f B/s", bytesPerSec);
        } else if (bytesPerSec < 1024 * 1024) {
            return String.format("%.2f KB/s", bytesPerSec / 1024);
        } else if (bytesPerSec < 1024 * 1024 * 1024) {
            return String.format("%.2f MB/s", bytesPerSec / (1024 * 1024));
        } else {
            return String.format("%.2f GB/s", bytesPerSec / (1024 * 1024 * 1024));
        }
    }

    public static void skipStream(InputStream in, long bytes) throws IOException {
        long skipped = 0;
        while (skipped < bytes) {
            long s = in.skip(bytes - skipped);
            if (s == 0) {
                if (in.read() == -1) break;
                s = 1;
            }
            skipped += s;
        }
    }


}