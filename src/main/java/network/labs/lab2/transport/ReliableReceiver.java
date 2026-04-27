package network.labs.lab2.transport;

import network.labs.lab2.common.Config;
import network.labs.lab2.common.UdpPacket;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.TreeMap;

public final class ReliableReceiver {
    private final DatagramSocket sock;
    private final InetSocketAddress peer;
    private final byte[] recvBuf = new byte[Config.PAYLOAD_SIZE + Config.HEADER_SIZE];
    private final TreeMap<Integer, byte[]> reorder = new TreeMap<>();
    private int nextSeq = 0, received = 0, ooo = 0;

    public ReliableReceiver(DatagramSocket sock, InetSocketAddress peer) {
        this.sock = sock; this.peer = peer;
    }

    public long receiveStream(OutputStream out, long expected) throws IOException {
        int old = sock.getSoTimeout(); sock.setSoTimeout(Config.SOCKET_TIMEOUT_MS);
        try {
            long start = System.currentTimeMillis();
            while (received < expected) {
                UdpPacket pkt = readPacket();
                if (pkt == null) continue;
                if (pkt.isFin()) break; // Завершаем при получении FIN
                if (!pkt.isData() || pkt.seq() < nextSeq) continue;
                sendAck(pkt.seq());
                if (pkt.seq() == nextSeq) {
                    out.write(pkt.dataUnsafe(), 0, pkt.dataLength());
                    received += pkt.dataLength();
                    flushBuffer(out);
                } else {
                    reorder.put(pkt.seq(), pkt.dataUnsafe()); ooo++;
                }
                printProgress(received, expected, start);
            }
        } finally { sock.setSoTimeout(old); }
        return received;
    }

    private UdpPacket readPacket() throws IOException {
        try {
            DatagramPacket dp = new DatagramPacket(recvBuf, recvBuf.length);
            sock.receive(dp);
            return UdpPacket.fromBytes(dp.getData(), dp.getLength());
        } catch (SocketTimeoutException e) { return null; }
    }

    private void sendAck(int seq) throws IOException {
        byte[] bytes = UdpPacket.ack(seq).toBytes();
        sock.send(new DatagramPacket(bytes, bytes.length, peer));
    }

    private void flushBuffer(OutputStream out) throws IOException {
        while (!reorder.isEmpty() && reorder.firstKey() == nextSeq) {
            byte[] d = reorder.pollFirstEntry().getValue();
            out.write(d); received += d.length; nextSeq++;
        }
    }

    private void printProgress(long cur, long tot, long start) {
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed <= 0 || cur % (1024*1024) != 0) return;
        double mbps = (cur * 8.0) / elapsed;
        System.out.printf("\r📥 %3d%% | %.1f Мбит/с | OOO: %d", cur*100/tot, mbps, ooo);
        if (cur >= tot) System.out.println();
    }
}