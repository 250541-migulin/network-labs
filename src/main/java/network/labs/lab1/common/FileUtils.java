package network.labs.lab1.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


/**
 * Утилиты для работы с файлами и директориями.
 */
public class FileUtils {
    private static final Logger log = LoggerFactory.getLogger(FileUtils.class);

    /**
     * Проверяет существование директории и создаёт её при необходимости.
     *
     * @param dir путь к директории
     * @throws IllegalStateException если директорию создать не удалось
     */
    public static void ensureDirectory(Path dir) {
        try {
            if (Files.exists(dir)) {
                log.info("Директория уже существует: {}", dir.toAbsolutePath());
            } else {
                Files.createDirectories(dir);
                log.info("Директория создана: {}", dir.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Не удалось создать директорию: {}", dir.toAbsolutePath(), e);
            throw new IllegalStateException("Не удалось создать директорию: " + dir.toAbsolutePath(), e);
        }
    }
}
