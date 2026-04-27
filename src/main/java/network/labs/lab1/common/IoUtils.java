package network.labs.lab1.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Утилиты для работы с потоками в кодировке UTF-8.
 * Включает логирование всех сетевых операций и прогресс-бары для передачи файлов.
 */
public class IoUtils {

    private static final Logger log = LoggerFactory.getLogger(IoUtils.class);

    /**
     * Читает строку до \r\n или \n в кодировке UTF-8.
     * Логирует факт чтения для отладки протокола.
     *
     * @param in InputStream для чтения
     * @return строку или null при конце потока
     */
    public static String readLine(InputStream in) throws IOException {
        log.debug("<< READ from network");

        var baos = new java.io.ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b == '\r') {
                in.mark(1);
                int next = in.read();
                if (next != '\n') in.reset();
                break;
            }
            baos.write(b);
        }
        byte[] bytes = baos.toByteArray();
        String result = bytes.length == 0 && b == -1 ? null : new String(bytes, StandardCharsets.UTF_8);

        log.debug("<< READ: '{}'", result);
        return result;
    }

    /**
     * Пишет строку с \r\n в кодировке UTF-8.
     * Логирует факт записи для отладки протокола.
     *
     * @param out OutputStream для записи
     * @param line строка для записи (без завершающей последовательности)
     */
    public static void writeLine(OutputStream out, String line) throws IOException {
        log.debug(">> WRITE to network: '{}'", line);

        out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();

        log.debug(">> WRITE: flushed");
    }

    /**
     * Копирует данные из сети в файл (для DOWNLOAD/UPLOAD приёма).
     * Выводит прогресс-бар с процентами и скоростью передачи.
     *
     * @param in InputStream из сокета
     * @param target Путь к файлу
     * @param append true = дописать (докачка), false = создать заново
     * @param maxBytes Максимальное количество байт для чтения
     * @return Количество записанных байт
     */
    public static long copyStreamToFile(InputStream in, Path target, boolean append, long maxBytes) throws IOException {
        log.debug(">> COPY: network -> file, maxBytes={}, append={}", maxBytes, append);

        long total = 0;
        byte[] buf = new byte[8192];
        long lastProgress = 0;
        long startTime = System.currentTimeMillis();
        long lastTime = startTime;
        long lastBytes = 0;

        try (var fos = Files.newOutputStream(target, StandardOpenOption.CREATE,
                append ? StandardOpenOption.APPEND : StandardOpenOption.WRITE)) {

            System.out.print("\r📥 Приём: 0%");

            int read;
            while (total < maxBytes && (read = in.read(buf, 0, (int) Math.min(buf.length, maxBytes - total))) != -1) {
                fos.write(buf, 0, read);
                total += read;

                long currentTime = System.currentTimeMillis();
                long progress = (total * 100) / maxBytes;

                // Выводим прогресс каждые 10%
                if (progress >= lastProgress + 10) {
                    long timeDiff = currentTime - lastTime;
                    long bytesDiff = total - lastBytes;
                    long speed = timeDiff > 0 ? (bytesDiff * 1000) / timeDiff : 0;

                    System.out.print("\r📥 Приём: " + progress + "% | " +
                            formatBytes(total) + "/" + formatBytes(maxBytes) +
                            " | " + formatBytes(speed) + "/с");

                    lastProgress = progress;
                    lastTime = currentTime;
                    lastBytes = total;
                }
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;
        long avgSpeed = totalTime > 0 ? (total * 1000) / totalTime : 0;

        System.out.println("\r📥 Приём: 100% — завершено! (" +
                formatBytes(total) + ", " + formatBytes(avgSpeed) + "/с)          ");

        log.debug(">> COPY: completed, total={} bytes", total);
        return total;
    }

    /**
     * Копирует данные из файла в сеть (для UPLOAD/DOWNLOAD отправки).
     * Выводит прогресс-бар с процентами и скоростью передачи.
     *
     * @param source Путь к файлу
     * @param out OutputStream в сокет
     * @param skip Сколько байт пропустить (для докачки)
     * @return Количество отправленных байт
     */
    public static long copyFileToStream(Path source, OutputStream out, long skip) throws IOException {
        log.debug(">> COPY: file -> network, skip={}", skip);

        long total = 0;
        byte[] buf = new byte[8192];
        long lastProgress = 0;
        long startTime = System.currentTimeMillis();
        long lastTime = startTime;
        long lastBytes = 0;

        try (var fis = Files.newInputStream(source)) {
            fis.skipNBytes(skip);

            long fileSize = Files.size(source);
            long remainingSize = fileSize - skip;

            System.out.print("\r📤 Отправка: 0%");

            int read;
            while ((read = fis.read(buf)) != -1) {
                out.write(buf, 0, read);
                total += read;

                long currentTime = System.currentTimeMillis();
                long progress = (total * 100) / remainingSize;

                // Выводим прогресс каждые 10%
                if (progress >= lastProgress + 10) {
                    long timeDiff = currentTime - lastTime;
                    long bytesDiff = total - lastBytes;
                    long speed = timeDiff > 0 ? (bytesDiff * 1000) / timeDiff : 0;

                    System.out.print("\r📤 Отправка: " + progress + "% | " +
                            formatBytes(total) + "/" + formatBytes(remainingSize) +
                            " | " + formatBytes(speed) + "/с");

                    lastProgress = progress;
                    lastTime = currentTime;
                    lastBytes = total;
                }
            }
            out.flush();
        }

        long totalTime = System.currentTimeMillis() - startTime;
        long avgSpeed = totalTime > 0 ? (total * 1000) / totalTime : 0;

        System.out.println("\r📤 Отправка: 100% — завершено! (" +
                formatBytes(total) + ", " + formatBytes(avgSpeed) + "/с)          ");

        log.debug(">> COPY: completed, total={} bytes", total);
        return total;
    }

    /**
     * Форматирует размер в байтах в человекочитаемый вид (B, KB, MB, GB).
     *
     * @param bytes размер в байтах
     * @return строка вида "10 MB", "512 KB" и т.д.
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)) + " MB";
        return (bytes / (1024 * 1024 * 1024)) + " GB";
    }
}