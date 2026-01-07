package network.labs.lab2.transport;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class UdpPacket implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public enum Type { DATA, ACK, ERROR }

    public final Type type;
    public final int seq;
    public final byte[] data;

    public UdpPacket(Type type, int seq, byte[] data) {
        this.type = type;
        this.seq = seq;
        this.data = data != null ? data : new byte[0];
    }

    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeByte(type.ordinal());
        dos.writeInt(seq);
        dos.writeInt(data.length);
        dos.write(data);
        return baos.toByteArray();
    }

    public static UdpPacket fromBytes(byte[] bytes, int length) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes, 0, length);
        DataInputStream dis = new DataInputStream(bais);
        int typeOrd = dis.readByte();
        int seq = dis.readInt();
        int len = dis.readInt();
        byte[] data = new byte[len];
        dis.readFully(data);
        Type type = Type.values()[typeOrd];
        return new UdpPacket(type, seq, data);
    }

    public String getDataAsString() {
        return new String(data, StandardCharsets.UTF_8);
    }
}
