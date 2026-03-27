package network.labs.lab2.transport;

import network.labs.lab2.common.Config;
import network.labs.lab2.common.UdpPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.TreeMap;

/**
 * Надёжный приём данных по UDP с подтверждениями.
 * Работает в одном потоке с коротким таймаутом для неблокирующего режима.
 */
public class ReliableUdpReceiver {

    private static final Logger log = LoggerFactory.getLogger(ReliableUdpReceiver.class);

    private final DatagramSocket socket;
    private final InetSocketAddress peer;
    private final OutputStream out;

    // Статистика
    private int packetsReceived = 0;
    private int packetsOutOfOrder = 0;
    private int packetsDuplicate = 0;

    /**
     * Создаёт получателя.
     *
     * @param socket сокет для приёма
     * @param peer адрес отправителя
     * @param out поток для записи данных
     */
    public ReliableUdpReceiver(DatagramSocket socket, InetSocketAddress peer, OutputStream out) {
        this.socket = socket;
        this.peer = peer;
        this.out = out;
    }

    /**
     * Принимает поток данных с надёжностью (однопоточная версия).
     *
     * @param expectedSize ожидаемый размер данных (байт)
     * @return количество полученных байт
     */
    public long receiveStream(long expectedSize) throws IOException {
        log.debug("receiveStream: старт, expectedSize={} байт", expectedSize);

        // Сохраняем оригинальный таймаут и устанавливаем короткий
        int originalTimeout = socket.getSoTimeout();
        socket.setSoTimeout(5); // 5 мс — не блокируемся надолго

        long startTime = System.currentTimeMillis();
        long lastProgressTime = startTime;

        // Буфер для пакетов, пришедших не по порядку (seq -> data)
        TreeMap<Integer, byte[]> reorderBuffer = new TreeMap<>();
        int expectedSeq = 0;
        long receivedBytes = 0;

        // Переиспользуемый буфер
        byte[] buf = new byte[Config.UDP_MAX_PAYLOAD + 7];

        System.out.print("\rПриём: 0%");

        while (receivedBytes < expectedSize) {

            // Пытаемся получить пакет с коротким таймаутом
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet); // Блокируется максимум на 5 мс
            } catch (java.net.SocketTimeoutException e) {
                // Таймаут 5 мс — нормально, просто продолжаем цикл
                continue;
            }

            // Проверка адреса (только IP, порт может меняться)
            InetSocketAddress sender = new InetSocketAddress(
                    packet.getAddress(), packet.getPort()
            );
            if (!sender.getAddress().equals(peer.getAddress())) {
                log.debug("receiveStream: пакет от другого хоста {}, игнорирую", sender);
                continue;
            }

            UdpPacket udpPacket;
            try {
                udpPacket = UdpPacket.fromBytes(packet.getData(), packet.getLength());
            } catch (IOException e) {
                log.warn("receiveStream: некорректный пакет, игнорирую");
                continue;
            }

            if (!udpPacket.isData()) {
                continue;
            }

            int seqNum = udpPacket.getSeqNum();
            byte[] data = udpPacket.getData();
            packetsReceived++;

            log.debug("receiveStream: получен пакет seq={}, len={}", seqNum, data.length);

            // Отправляем ACK немедленно
            sendAck(seqNum);

            // Проверяем порядок
            if (seqNum == expectedSeq) {
                // Ожидаемый пакет — записываем сразу
                out.write(data);
                receivedBytes += data.length;
                expectedSeq++;
                log.debug("receiveStream: записан пакет seq={}, receivedBytes={}",
                        seqNum, receivedBytes);

                // Проверяем буфер пересортировки
                while (!reorderBuffer.isEmpty() &&
                        reorderBuffer.firstKey() == expectedSeq) {
                    byte[] buffered = reorderBuffer.pollFirstEntry().getValue();
                    out.write(buffered);
                    receivedBytes += buffered.length;
                    expectedSeq++;
                    packetsOutOfOrder--;
                    log.debug("receiveStream: записан из буфера seq={}, receivedBytes={}",
                            expectedSeq - 1, receivedBytes);
                }

            } else if (seqNum < expectedSeq) {
                // Дубликат
                packetsDuplicate++;
                log.debug("receiveStream: дубликат seq={}, игнорирую", seqNum);

            } else {
                // Пришёл не по порядку — в буфер
                reorderBuffer.put(seqNum, data);
                packetsOutOfOrder++;
                log.debug("receiveStream: пакет не по порядку seq={}, в буфер", seqNum);
            }

            // Обновление прогресса (каждые 10%)
            long progress = (receivedBytes * 100) / expectedSize;
            long now = System.currentTimeMillis();
            if (progress > 0 && progress % 10 == 0 && now - lastProgressTime >= 100) {
                long elapsed = now - startTime;
                long speed = elapsed > 0 ? (receivedBytes * 1000) / elapsed : 0;

                System.out.print("\rПриём: " + progress + "% | " +
                        formatBytes(receivedBytes) + "/" + formatBytes(expectedSize) +
                        " | " + formatBytes(speed) + "/с");

                lastProgressTime = now;
            }
        }

        // Финальная статистика
        long totalTime = System.currentTimeMillis() - startTime;
        long avgSpeed = totalTime > 0 ? (receivedBytes * 1000) / totalTime : 0;

        System.out.println("\rПриём: 100% — завершено! (" +
                formatBytes(receivedBytes) + ", " + formatBytes(avgSpeed) + "/с)          ");

        log.info("Получено {} байт за {} мс | Битрейт: {} Мбит/с",
                receivedBytes, totalTime, String.format("%.1f",
                        (receivedBytes * 8.0 * 1000) / (totalTime * 1_000_000)));

        // Восстанавливаем оригинальный таймаут
        socket.setSoTimeout(originalTimeout);

        log.debug("receiveStream: завершение, receivedBytes={}", receivedBytes);
        return receivedBytes;
    }

    /**
     * Отправляет подтверждение (ACK) для указанного номера пакета.
     */
    private void sendAck(int seqNum) throws IOException {
        UdpPacket ack = UdpPacket.createAck(seqNum);
        byte[] bytes = ack.toBytes();
        DatagramPacket datagram = new DatagramPacket(bytes, bytes.length, peer);
        socket.send(datagram);
        log.debug("sendAck: отправлен ACK для {}", seqNum);
    }

    /**
     * Форматирует размер в байтах в человекочитаемый вид.
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)) + " MB";
        return (bytes / (1024 * 1024 * 1024)) + " GB";
    }
}