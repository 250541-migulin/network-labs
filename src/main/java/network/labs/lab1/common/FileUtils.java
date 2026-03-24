package network.labs.lab1.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Утилиты для работы с файлами.
 */
public class FileUtils {
    private static final Logger log = LoggerFactory.getLogger(FileUtils.class);


    /**
     * Создаёт директорию (если нет) и очищает её содержимое.
     * Используется для подготовки рабочих папок при старте.
     *
     * @param dir путь к директории (например, "files/server_tcp/upload")
     */
    public static void prepareDirectory(Path dir) {
        try {
            // Создаём папку + все родительские, если их нет
            Files.createDirectories(dir);
            log.debug("Директория создана: {}", dir);

            // Очищаем содержимое (удаляем файлы)
            try (var files = Files.list(dir)) {
                files.forEach(path -> {
                    try {
                        Files.delete(path);
                        log.debug("Удалён файл: {}", path.getFileName());
                    } catch (IOException e) {
                        log.warn("Не удалось удалить: {}", path.getFileName());
                    }
                });
            }

        } catch (IOException e) {
            log.error("Ошибка подготовки директории: {}", dir, e);
        }
    }
}