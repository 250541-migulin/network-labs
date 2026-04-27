package network.labs.lab2.transport;

import network.labs.lab2.common.Config;
import network.labs.lab2.common.UdpPacket;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayDeque;
import java.util.Queue;

public final class ReliableSender {
    private final DatagramSocket sock;
    private final InetSocketAddress peer;
    private final byte[] readBuf = new byte[Config.PAYLOAD_SIZE];
    private final byte[] ackBuf = new byte[64];
    private long sent = 0, acked = 0, retx = 0;

    public ReliableSender(DatagramSocket sock, InetSocketAddress peer) {
        this.sock = sock; this.peer = peer;
    }

    public void sendStream(InputStream in, long total) throws IOException {
        int old = sock.getSoTimeout(); sock.setSoTimeout(Config.SOCKET_TIMEOUT_MS);
        try {
            Queue<Pending> win = new ArrayDeque<>(Config.WINDOW_SIZE);
            int seq = 0; long start = System.currentTimeMillis();
            while (sent < total || !win.isEmpty()) {
                fillWindow(in, win, total, seq);
                seq = processAcks(win);
                checkTimeouts(win);
                printProgress(sent, total, start);
            }
            byte[] fin = UdpPacket.fin(seq).toBytes();
            sock.send(new DatagramPacket(fin, fin.length, peer));
        } finally { sock.setSoTimeout(old); }
    }

    private void fillWindow(InputStream in, Queue<Pending> win, long total, int seq) throws IOException {
        while (win.size() < Config.WINDOW_SIZE && sent < total) {
            int toRead = (int) Math.min(Config.PAYLOAD_SIZE, total - sent);
            int read = in.read(readBuf, 0, toRead); if (read <= 0) break;
            UdpPacket pkt = UdpPacket.data(seq, readBuf, 0, read);
            byte[] bytes = pkt.toBytes();
            sock.send(new DatagramPacket(bytes, bytes.length, peer));
            win.add(new Pending(seq, bytes, read, System.currentTimeMillis()));
            seq++; sent += read;
        }
    }

    private int processAcks(Queue<Pending> win) throws IOException {
        int last = win.isEmpty() ? 0 : win.peek().seq - 1;
        try {
            DatagramPacket dp = new DatagramPacket(ackBuf, ackBuf.length);
            sock.receive(dp);
            UdpPacket p = UdpPacket.fromBytes(dp.getData(), dp.getLength());
            if (p.isAck()) {
                int ackNum = p.ack();
                while (!win.isEmpty() && win.peek().seq <= ackNum) {
                    Pending x = win.poll(); acked += x.size; last = Math.max(last, x.seq);
                }
            }
        } catch (SocketTimeoutException ignored) {}
        return last + 1;
    }

    private void checkTimeouts(Queue<Pending> win) throws IOException {
        long now = System.currentTimeMillis();
        for (Pending p : win) {
            if (now - p.sentAt > Config.ACK_TIMEOUT_MS && p.retries < Config.MAX_RETRIES) {
                sock.send(new DatagramPacket(p.bytes, p.bytes.length, peer));
                p.sentAt = now; p.retries++; retx++;
            }
        }
    }

    private void printProgress(long cur, long tot, long start) {
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed <= 0 || cur % (1024*1024) != 0) return;
        double mbps = (cur * 8.0) / elapsed;
        System.out.printf("\r📤 %3d%% | %.1f Мбит/с | Retx: %d", cur*100/tot, mbps, retx);
        if (cur >= tot) System.out.println();
    }

    private static class Pending {
        final int seq; final byte[] bytes; final int size; long sentAt; int retries;
        Pending(int s, byte[] b, int sz, long t) { seq=s; bytes=b; size=sz; sentAt=t; retries=0; }
    }
}