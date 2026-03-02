package network.labs.lab2.transport;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class DataPacket {
    public final int seqNum;
    public final byte[] data;

    public DataPacket(int seqNum, byte[] data) {
        this.seqNum = seqNum;
        this.data = data != null ? data : new byte[0];
    }

    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(seqNum);
        dos.writeInt(data.length);
        dos.write(data);
        return baos.toByteArray();
    }

    public static DataPacket fromBytes(byte[] bytes) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        DataInputStream dis = new DataInputStream(bais);
        int seq = dis.readInt();
        int len = dis.readInt();
        byte[] data = new byte[len];
        dis.readFully(data);
        return new DataPacket(seq, data);
    }
}