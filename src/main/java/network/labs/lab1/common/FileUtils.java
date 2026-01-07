package network.labs.lab1.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileUtils {
    private static final Logger log = LoggerFactory.getLogger(FileUtils.class);

    public static void ensureDirectory(Path dir) {
        try {
            if (Files.exists(dir)) {
                log.info("Директория уже существует: {}", dir.toAbsolutePath());
            } else {
                Files.createDirectories(dir);
                log.info("Создана директория: {}", dir.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Не удалось создать директорию: {}", dir.toAbsolutePath(), e);
            throw new IllegalStateException("Не удалось создать директорию: " + dir, e);
        }
    }
}