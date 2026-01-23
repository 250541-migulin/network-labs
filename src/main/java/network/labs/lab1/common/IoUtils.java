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

    public static String formatTransferRate(long bytes, long millis) {
        if (millis <= 0) return bytes + " байт за " + millis + " мс (—)";
        double seconds = millis / 1000.0;
        double kb = bytes / 1024.0;
        double rate = kb / seconds;
        return bytes + " байт за " + millis + " мс (" + String.format("%.2f", rate) + " KB/s)";
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