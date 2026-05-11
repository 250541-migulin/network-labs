package network.labs.lab1.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class IoUtils {
    private IoUtils() {}

    public static String readLine(InputStream in) throws IOException {
        var baos = new java.io.ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b == '\r') {
                int next = in.read();
                if (next == '\n' || next == -1) break;
                // next != '\n' && next != -1: байт "теряется", но протокол использует \r\n
                break;
            }
            baos.write(b);
        }
        byte[] bytes = baos.toByteArray();
        return (bytes.length == 0 && b == -1) ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    public static void writeLine(OutputStream out, String line) throws IOException {
        out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    public static long copyStreamToFile(InputStream in, Path target, boolean append, long maxBytes) throws IOException {
        long total = 0;
        byte[] buf = new byte[8192];
        try (var fos = Files.newOutputStream(target, StandardOpenOption.CREATE,
                append ? StandardOpenOption.APPEND : StandardOpenOption.WRITE)) {
            while (total < maxBytes) {
                int toRead = (int) Math.min(buf.length, maxBytes - total);
                int read = in.read(buf, 0, toRead);
                if (read == -1) break;
                fos.write(buf, 0, read);
                total += read;
            }
        }
        return total;
    }

    public static long copyFileToStream(Path source, OutputStream out, long skip) throws IOException {
        long total = 0;
        byte[] buf = new byte[8192];
        long remaining = Files.size(source) - skip;
        try (var fis = Files.newInputStream(source)) {
            fis.skipNBytes(skip);
            while (total < remaining) {
                int toRead = (int) Math.min(buf.length, remaining - total);
                int read = fis.read(buf, 0, toRead);
                if (read == -1) break;
                out.write(buf, 0, read);
                total += read;
            }
            out.flush();
        }
        return total;
    }
}