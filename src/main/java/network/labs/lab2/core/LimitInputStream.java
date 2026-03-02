package network.labs.lab2.core;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Ограничивает количество байт, читаемых из потока.
 */
public class LimitInputStream extends FilterInputStream {
    private long remaining;

    public LimitInputStream(InputStream in, long limit) {
        super(in);
        this.remaining = limit;
    }

    @Override
    public int read() throws IOException {
        if (remaining <= 0) return -1;
        int b = in.read();
        if (b != -1) remaining--;
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (remaining <= 0) return -1;
        int bytesRead = in.read(b, off, (int) Math.min(len, remaining));
        if (bytesRead > 0) remaining -= bytesRead;
        return bytesRead;
    }
}