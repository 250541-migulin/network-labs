package network.labs.lab2.transport;

import network.labs.lab2.common.Config;
import network.labs.lab2.common.IoUtils;
import network.labs.lab2.common.UdpPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

/**
 * Надёжная отправка данных по UDP с скользящим окном.
 * Работает в одном потоке с коротким таймаутом для неблокирующего режима.
 */
public class ReliableUdpSender {

    private static final Logger log = LoggerFactory.getLogger(ReliableUdpSender.class);

    private final DatagramSocket socket;
    private final InetSocketAddress peer;

    // Статистика
    private int packetsSent = 0;
    private int packetsAcked = 0;
    private int packetsRetried = 0;
    private int packetsLost = 0;

    /**
     * Создаёт отправителя.
     *
     * @param socket сокет для отправки
     * @param peer адрес получателя
     */
    public ReliableUdpSender(DatagramSocket socket, InetSocketAddress peer) {
        this.socket = socket;
        this.peer = peer;
    }

    /**
     * Отправляет поток данных с надёжностью
     *
     * @param in входной поток с данными
     * @param totalSize ожидаемый размер данных (байт)
     * @return количество отправленных байт
     */
    public long sendStream(InputStream in, long totalSize) throws IOException {
        log.debug("sendStream: старт, totalSize={} байт", totalSize);

        // Сохраняем оригинальный таймаут и устанавливаем короткий
        int originalTimeout = socket.getSoTimeout();
        socket.setSoTimeout(5); // 5 мс — не блокируемся надолго

        long startTime = System.currentTimeMillis();
        long lastProgressTime = startTime;
        long lastProgressBytes = 0;

        // Окно неотвеченных пакетов
        Queue<PendingPacket> window = new ArrayDeque<>();
        int baseSeq = 0;
        int nextSeq = 0;
        long sentBytes = 0;

        // Буферы переиспользуются для уменьшения аллокаций
        byte[] buffer = new byte[Config.UDP_MAX_PAYLOAD];
        byte[] ackBuf = new byte[64];

        System.out.print("\rОтправка: 0%");

        try {
            while (sentBytes < totalSize || !window.isEmpty()) {

                // 1. Отправляем новые пакеты в пределах окна
                while (nextSeq < baseSeq + Config.UDP_WINDOW_SIZE && sentBytes < totalSize) {
                    int toRead = (int) Math.min(Config.UDP_MAX_PAYLOAD, totalSize - sentBytes);
                    int read = in.read(buffer, 0, toRead);
                    if (read <= 0) break;

                    byte[] data = Arrays.copyOf(buffer, read);
                    UdpPacket packet = UdpPacket.createData(nextSeq, data);

                    sendPacket(packet);
                    window.offer(new PendingPacket(packet, System.currentTimeMillis(), 0));

                    nextSeq++;
                    sentBytes += read;
                    packetsSent++;
                }

                // 2. Получаем ВСЕ доступные ACK (не блокируемся надолго)
                boolean receivedAny = false;
                while (true) {
                    try {
                        DatagramPacket ackDatagram = new DatagramPacket(ackBuf, ackBuf.length);
                        socket.receive(ackDatagram); // Блокируется максимум на 5 мс

                        // Проверка адреса (только IP, порт может меняться)
                        InetSocketAddress sender = new InetSocketAddress(
                                ackDatagram.getAddress(), ackDatagram.getPort()
                        );
                        if (!sender.getAddress().equals(peer.getAddress())) {
                            continue;
                        }

                        UdpPacket ackPacket = UdpPacket.fromBytes(
                                ackDatagram.getData(), ackDatagram.getLength()
                        );

                        if (ackPacket.isAck()) {
                            int ackNum = ackPacket.getAckNum();
                            log.debug("sendStream: получен ACK для пакета {}", ackNum);

                            // Сдвигаем окно
                            while (!window.isEmpty() &&
                                    window.peek().packet.getSeqNum() <= ackNum) {
                                window.poll();
                                packetsAcked++;
                            }
                            baseSeq = ackNum + 1;
                            receivedAny = true;
                        }

                    } catch (SocketTimeoutException e) {
                        break;
                    }
                }

                // 3. Проверка на повторную отправку (если пакет завис в окне)
                long now = System.currentTimeMillis();
                for (PendingPacket pending : window) {
                    if (now - pending.sentTime > Config.UDP_ACK_TIMEOUT_MS) {
                        pending.retryCount++;
                        if (pending.retryCount <= Config.UDP_MAX_RETRIES) {
                            sendPacket(pending.packet);
                            pending.sentTime = now;
                            packetsRetried++;
                            log.debug("sendStream: повторная отправка seq={}",
                                    pending.packet.getSeqNum());
                        } else {
                            packetsLost++;
                            log.warn("sendStream: пакет потерян seq={}",
                                    pending.packet.getSeqNum());
                        }
                    }
                }

                // 5. Обновление прогресса (каждые 10%)
                long progress = (sentBytes * 100) / totalSize;
                long currentTime = System.currentTimeMillis();
                if (progress > 0 && progress % 10 == 0 &&
                        currentTime - lastProgressTime >= 100) {

                    long elapsed = currentTime - startTime;
                    long speed = elapsed > 0 ? (sentBytes * 1000) / elapsed : 0;
                    long recentSpeed = currentTime - lastProgressTime > 0 ?
                            ((sentBytes - lastProgressBytes) * 1000) /
                                    (currentTime - lastProgressTime) : 0;

                    System.out.print("\rОтправка: " + progress + "% | " +
                            formatBytes(sentBytes) + "/" + formatBytes(totalSize) +
                            " | " + formatBytes(recentSpeed) + "/с");

                    lastProgressTime = currentTime;
                    lastProgressBytes = sentBytes;
                }
            }

            // Финальная статистика
            long totalTime = System.currentTimeMillis() - startTime;
            long avgSpeed = totalTime > 0 ? (sentBytes * 1000) / totalTime : 0;

            System.out.println("\rОтправка: 100% — завершено! (" +
                    formatBytes(sentBytes) + ", " + formatBytes(avgSpeed) + "/с)          ");

            log.info("Отправлено {} байт", sentBytes);
            log.info("Пакетов отправлено: {}", packetsSent);
            log.info("Пакетов подтверждено: {}", packetsAcked);
            log.info("Потеряно пакетов: {} ({:.2f}%)", packetsLost,
                    packetsSent > 0 ? (packetsLost * 100.0 / packetsSent) : 0);
            log.info("Повторных отправок: {}", packetsRetried);
            log.info("Битрейт: {} Мбит/с", String.format("%.1f",
                    (sentBytes * 8.0 * 1000) / (totalTime * 1_000_000)));

        } finally {
            // Восстанавливаем оригинальный таймаут
            socket.setSoTimeout(originalTimeout);
        }

        log.debug("sendStream: завершение, sentBytes={}", sentBytes);
        return sentBytes;
    }

    /**
     * Отправляет один пакет.
     */
    private void sendPacket(UdpPacket packet) throws IOException {
        byte[] bytes = packet.toBytes();
        DatagramPacket datagram = new DatagramPacket(bytes, bytes.length, peer);
        socket.send(datagram);
        log.debug("sendPacket: отправлен {}", packet);
    }

    /**
     * Форматирует размер в байтах в человекочитаемый вид.
     */
    private String formatBytes(long bytes) {
        return IoUtils.formatBytes(bytes);
    }

    /**
     * Внутренний класс для отслеживания отправленных пакетов.
     */
    private static class PendingPacket {
        final UdpPacket packet;
        long sentTime;
        int retryCount;

        PendingPacket(UdpPacket packet, long sentTime, int retryCount) {
            this.packet = packet;
            this.sentTime = sentTime;
            this.retryCount = retryCount;
        }
    }
}