package network.labs.lab3.server;

import network.labs.lab3.common.Config;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Состояние подключения одного клиента в мультиплексированном сервере.
 */
public final class ClientSession {

    private static final AtomicLong ID_COUNTER = new AtomicLong(0);

    public enum State {
        IDLE,
        WAITING_UPLOAD_SIZE,
        WAITING_DOWNLOAD_SIZE,
        UPLOADING,
        DOWNLOADING
    }

    public final String id;
    public final SocketChannel channel;
    public final ByteBuffer netBuf = ByteBuffer.allocate(Config.BUFFER_SIZE);
    public final StringBuilder commandBuffer = new StringBuilder();

    public State state = State.IDLE;
    public Path filePath;
    public long bytesExpected;
    public long bytesProcessed;
    public FileChannel downloadFileCh;
    public FileChannel uploadFileCh;
    public long transferStartTime; // Время начала передачи для расчёта скорости

    public ClientSession(SocketChannel ch) {
        this.channel = ch;
        this.id = "S" + ID_COUNTER.incrementAndGet();
    }

    /**
     * Сбрасывает сессию в начальное состояние. Закрывает файловые каналы.
     */
    public void reset() {
        state = State.IDLE;
        bytesExpected = 0;
        bytesProcessed = 0;
        filePath = null;
        commandBuffer.setLength(0);
        netBuf.clear();
        transferStartTime = 0;

        if (downloadFileCh != null) {
            try { downloadFileCh.close(); } catch (Exception ignored) {}
            downloadFileCh = null;
        }
        if (uploadFileCh != null) {
            try { uploadFileCh.close(); } catch (Exception ignored) {}
            uploadFileCh = null;
        }
    }
}