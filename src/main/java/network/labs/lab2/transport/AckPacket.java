package network.labs.lab2.transport;

import java.io.*;

public class AckPacket {
    public final int ackNum;

    public AckPacket(int ackNum) {
        this.ackNum = ackNum;
    }

    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(ackNum);
        return baos.toByteArray();
    }

    public static AckPacket fromBytes(byte[] bytes) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        DataInputStream dis = new DataInputStream(bais);
        int ack = dis.readInt();
        return new AckPacket(ack);
    }
}