package network.labs.lab2.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * UDP-пакет с 7-байтовым заголовком.
 * Формат: Seq(2) | ACK(2) | Flags(1) | Len(2) | Data(0-1465)
 */
public final class UdpPacket {
    public static final byte FLAG_DATA = 0x01;
    public static final byte FLAG_ACK  = 0x02;
    public static final byte FLAG_CMD  = 0x04;
    public static final byte FLAG_FIN  = 0x08;

    private final int seqNum, ackNum;
    private final byte flags;
    private final byte[] data;

    private UdpPacket(int seq, int ack, byte flags, byte[] data) {
        this.seqNum = seq & 0xFFFF;
        this.ackNum = ack & 0xFFFF;
        this.flags = flags;
        this.data = data != null ? data : new byte[0];
    }

    // 🔥 Zero-copy доступ к данным (для скорости)
    public byte[] dataUnsafe() { return data; }
    public int dataLength() { return data.length; }
    public int seq() { return seqNum; }
    public int ack() { return ackNum; }
    public byte flags() { return flags; }

    public boolean isData() { return (flags & FLAG_DATA) != 0; }
    public boolean isAck()  { return (flags & FLAG_ACK) != 0; }
    public boolean isCmd()  { return (flags & FLAG_CMD) != 0; }
    public boolean isFin()  { return (flags & FLAG_FIN) != 0; }

    public static UdpPacket cmd(String text) {
        return new UdpPacket(0, 0, FLAG_CMD, text.getBytes(StandardCharsets.UTF_8));
    }

    public static UdpPacket ack(int seq) {
        return new UdpPacket(0, seq, FLAG_ACK, new byte[0]);
    }

    // 🔥 Без копирования буфера (вызывающий гарантирует неизменность до отправки)
    public static UdpPacket data(int seq, byte[] buf, int off, int len) {
        int max = Math.min(len, Config.PAYLOAD_SIZE);
        if (off == 0 && max == buf.length) return new UdpPacket(seq, 0, FLAG_DATA, buf);
        return new UdpPacket(seq, 0, FLAG_DATA, Arrays.copyOfRange(buf, off, off + max));
    }

    public static UdpPacket fin(int seq) {
        return new UdpPacket(seq, 0, FLAG_FIN, new byte[0]);
    }

    // ⚡ Сериализация в байты
    public byte[] toBytes() {
        byte[] out = new byte[Config.HEADER_SIZE + data.length];
        int i = 0;
        out[i++] = (byte)((seqNum >>> 8) & 0xFF); out[i++] = (byte)(seqNum & 0xFF);
        out[i++] = (byte)((ackNum >>> 8) & 0xFF); out[i++] = (byte)(ackNum & 0xFF);
        out[i++] = flags;
        out[i++] = (byte)((data.length >>> 8) & 0xFF); out[i++] = (byte)(data.length & 0xFF);
        if (data.length > 0) System.arraycopy(data, 0, out, i, data.length);
        return out;
    }

    public static UdpPacket fromBytes(byte[] buf, int len) throws IOException {
        if (len < Config.HEADER_SIZE) throw new IOException("Короткий пакет");
        int i = 0;
        int seq = ((buf[i++] & 0xFF) << 8) | (buf[i++] & 0xFF);
        int ack = ((buf[i++] & 0xFF) << 8) | (buf[i++] & 0xFF);
        byte flags = buf[i++];
        int dlen = ((buf[i++] & 0xFF) << 8) | (buf[i++] & 0xFF);
        dlen = Math.min(dlen, len - Config.HEADER_SIZE);
        byte[] d = dlen > 0 ? Arrays.copyOfRange(buf, i, i + dlen) : new byte[0];
        return new UdpPacket(seq, ack, flags, d);
    }
}