package network.labs.lab3.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Утилиты для работы с файлами (чтение/запись).
 */
public final class FileUtils {
    private FileUtils() {}

    /** Проверка существования файла */
    public static boolean exists(String path) {
        return new File(path).exists();
    }

    /** Получить размер файла */
    public static long size(String path) {
        return new File(path).length();
    }

    /** Открыть файл для чтения */
    public static FileInputStream openRead(String path) throws IOException {
        return new FileInputStream(new File(path));
    }

    /** Открыть файл для записи (перезапись) */
    public static FileOutputStream openWrite(String path) throws IOException {
        return new FileOutputStream(new File(path));
    }

    /** Открыть файл для записи (добавление) */
    public static FileOutputStream openAppend(String path) throws IOException {
        return new FileOutputStream(new File(path), true);
    }
}
