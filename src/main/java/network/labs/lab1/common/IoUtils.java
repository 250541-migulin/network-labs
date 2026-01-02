package network.labs.lab1.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Утилиты ввода-вывода для безопасной работы с потоками.
 * Гарантируют корректное чтение/запись строк и предотвращают десинхронизацию
 * между текстовыми и бинарными операциями.
 */
public class IoUtils {

    /**
     * Читает одну строку из InputStream до \n или \r\n.
     * Поддерживает оба формата окончания строки (telnet и netcat).
     *
     * @return прочитанная строка без \r\n / \n, или null при EOF
     * @throws IOException при ошибках ввода-вывода
     */
    /**
     * Читает строку в UTF-8 до \n или \r\n.
     * Корректно обрабатывает многобайтовые символы (кириллица, эмодзи и т.д.).
     */
    public static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                break;
            }
            if (b == '\r') {
                in.mark(1);
                int next = in.read();
                if (next != '\n') {
                    in.reset();
                }
                break;
            }
            buffer.write(b);
        }
        byte[] bytes = buffer.toByteArray();
        return bytes.length == 0 && b == -1 ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Записывает строку в OutputStream с окончанием \r\n.
     *
     * @throws IOException при ошибках ввода-вывода
     */
    public static void writeLine(OutputStream out, String line) throws IOException {
        out.write(line.getBytes(StandardCharsets.UTF_8));
        out.write('\r');
        out.write('\n');
        out.flush();
    }

    /**
     * Копирует ровно length байт из in в out.
     * Возвращает фактически прочитанное количество байт (<= length).
     *
     * @throws IOException при ошибках ввода-вывода
     */
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
}