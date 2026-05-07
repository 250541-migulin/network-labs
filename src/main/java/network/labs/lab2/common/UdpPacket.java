package network.labs.lab2.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Модель пакета прикладного протокола поверх UDP для лабораторной работы №2.
 *
 * Формат заголовка (7 байт, порядок байт - big-endian):
 * <pre>
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |          SeqNum (2)           |          AckNum (2)           |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |    Flags (1)  |         DataLength (2)        |    Data...    |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 *
 * Этот заголовок добавляется к полезной нагрузке перед сериализацией и
 * интерпретируется только на уровне приложения. Для ядра ОС это просто
 * часть UDP payload.
 *
 * Класс реализует фабричный паттерн: конструктор приватный, объекты
 * создаются через статические методы {@code cmd()}, {@code ack()},
 * {@code data()}, {@code fin()}.
 */
public final class UdpPacket {

    // ========================================================================
    // Флаги протокола (битовая маска)
    // ========================================================================

    /** Пакет содержит полезные данные файла (флаг 0x01). */
    public static final byte FLAG_DATA = 0x01;

    /** Пакет является подтверждением получения (ACK, флаг 0x02). */
    public static final byte FLAG_ACK  = 0x02;

    /** Пакет содержит текстовую команду управления (флаг 0x04). */
    public static final byte FLAG_CMD  = 0x04;

    /** Пакет сигнализирует о завершении передачи потока (флаг 0x08). */
    public static final byte FLAG_FIN  = 0x08;

    // ========================================================================
    // Поля пакета
    // ========================================================================

    /** Порядковый номер пакета (16 бит, 0..65535, зацикливается). */
    private final int seqNum;

    /** Номер последнего принятого по порядку пакета (кумулятивный ACK, 16 бит). */
    private final int ackNum;

    /** Битовая маска флагов: определяет тип пакета (DATA/ACK/CMD/FIN). */
    private final byte flags;

    /** Полезная нагрузка: данные файла или текст команды. */
    private final byte[] data;

    /**
     * Приватный конструктор.
     *
     * Используется только фабричными методами для обеспечения инвариантов:
     * - seq/ack обрезаются до 16 бит (& 0xFFFF)
     * - null-данные заменяются на пустой массив
     *
     * @param seq порядковый номер пакета
     * @param ack номер подтверждённого пакета (для ACK-пакетов)
     * @param flags битовая маска типа пакета
     * @param data полезные данные (может быть null)
     */
    private UdpPacket(int seq, int ack, byte flags, byte[] data) {
        this.seqNum = seq & 0xFFFF;
        this.ackNum = ack & 0xFFFF;
        this.flags = flags;
        this.data = data != null ? data : new byte[0];
    }

    // ========================================================================
    // Геттеры (без копирования для производительности)
    // ========================================================================

    /**
     * Возвращает прямой доступ к массиву данных без копирования.
     *
     * <b>Важно:</b> вызывающий код не должен модифицировать возвращённый
     * массив, так как это изменит внутреннее состояние пакета.
     * Используется в горячем пути передачи для zero-copy сериализации.
     *
     * @return ссылка на внутренний массив данных
     */
    public byte[] dataUnsafe() {
        return data;
    }

    /**
     * Возвращает длину полезных данных в байтах.
     *
     * @return размер поля data
     */
    public int dataLength() {
        return data.length;
    }

    /**
     * Возвращает порядковый номер пакета.
     *
     * @return seqNum (0..65535)
     */
    public int seq() {
        return seqNum;
    }

    /**
     * Возвращает номер подтверждённого пакета (для ACK-пакетов).
     *
     * @return ackNum (0..65535)
     */
    public int ack() {
        return ackNum;
    }

    /**
     * Возвращает битовую маску флагов.
     *
     * @return flags
     */
    public byte flags() {
        return flags;
    }

    // ========================================================================
    // Проверка типа пакета (битовые операции)
    // ========================================================================

    /**
     * Проверяет, является ли пакет контейнером данных файла.
     *
     * @return true, если установлен флаг FLAG_DATA
     */
    public boolean isData() {
        return (flags & FLAG_DATA) != 0;
    }

    /**
     * Проверяет, является ли пакет подтверждением (ACK).
     *
     * @return true, если установлен флаг FLAG_ACK
     */
    public boolean isAck() {
        return (flags & FLAG_ACK) != 0;
    }

    /**
     * Проверяет, является ли пакет текстовой командой.
     *
     * @return true, если установлен флаг FLAG_CMD
     */
    public boolean isCmd() {
        return (flags & FLAG_CMD) != 0;
    }

    /**
     * Проверяет, является ли пакет сигналом завершения передачи.
     *
     * @return true, если установлен флаг FLAG_FIN
     */
    public boolean isFin() {
        return (flags & FLAG_FIN) != 0;
    }

    // ========================================================================
    // Фабричные методы (создание пакетов разных типов)
    // ========================================================================

    /**
     * Создаёт пакет с текстовой командой.
     *
     * Используется для управляющих сообщений: ECHO, TIME, UPLOAD, DOWNLOAD.
     * Seq и Ack устанавливаются в 0, так как команды не участвуют в
     * механизме скользящего окна.
     *
     * @param text текст команды (кодируется в UTF-8)
     * @return новый экземпляр UdpPacket с флагом CMD
     */
    public static UdpPacket cmd(String text) {
        return new UdpPacket(0, 0, FLAG_CMD, text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Создаёт пакет подтверждения (ACK).
     *
     * Поле seq устанавливается в 0, поле ack содержит номер последнего
     * принятого по порядку пакета данных. Это реализует кумулятивное
     * подтверждение: получая ACK=N, отправитель понимает, что пакеты
     * 0..N получены успешно.
     *
     * @param seq номер пакета, который подтверждается
     * @return новый экземпляр UdpPacket с флагом ACK
     */
    public static UdpPacket ack(int seq) {
        return new UdpPacket(0, seq, FLAG_ACK, new byte[0]);
    }

    /**
     * Создаёт пакет с данными файла.
     *
     * Оптимизация: если буфер уже имеет нужный размер и смещение,
     * ссылка на него сохраняется без копирования (zero-copy).
     * В противном случае создаётся копия нужного диапазона.
     *
     * @param seq порядковый номер пакета
     * @param buf исходный буфер с данными
     * @param off смещение начала данных в буфере
     * @param len количество байт для включения в пакет
     * @return новый экземпляр UdpPacket с флагом DATA
     */
    public static UdpPacket data(int seq, byte[] buf, int off, int len) {
        int max = Math.min(len, Config.PAYLOAD_SIZE);
        // Zero-copy оптимизация: если буфер идеально подходит, не копируем
        if (off == 0 && max == buf.length) {
            return new UdpPacket(seq, 0, FLAG_DATA, buf);
        }
        // Иначе копируем только нужный диапазон
        return new UdpPacket(seq, 0, FLAG_DATA, Arrays.copyOfRange(buf, off, off + max));
    }

    /**
     * Создаёт пакет завершения передачи (FIN).
     *
     * Сигнализирует получателю, что поток данных завершён и можно
     * закрывать файл/сессию. Не содержит полезных данных.
     *
     * @param seq порядковый номер финального пакета
     * @return новый экземпляр UdpPacket с флагом FIN
     */
    public static UdpPacket fin(int seq) {
        return new UdpPacket(seq, 0, FLAG_FIN, new byte[0]);
    }

    // ========================================================================
    // Сериализация / Десериализация (бинарный формат)
    // ========================================================================

    /**
     * Сериализует пакет в байтовый массив для отправки по сети.
     *
     * Формат (big-endian):
     * - байты 0-1: seqNum (2 байта)
     * - байты 2-3: ackNum (2 байта)
     * - байт 4: flags (1 байт)
     * - байты 5-6: dataLength (2 байта)
     * - байты 7+: полезные данные
     *
     * @return байтовый массив, готовый к отправке через DatagramSocket
     */
    public byte[] toBytes() {
        byte[] out = new byte[Config.HEADER_SIZE + data.length];
        int i = 0;
        // SeqNum (2 байта, big-endian)
        out[i++] = (byte) ((seqNum >>> 8) & 0xFF);
        out[i++] = (byte) (seqNum & 0xFF);
        // AckNum (2 байта, big-endian)
        out[i++] = (byte) ((ackNum >>> 8) & 0xFF);
        out[i++] = (byte) (ackNum & 0xFF);
        // Flags (1 байт)
        out[i++] = flags;
        // DataLength (2 байта, big-endian)
        out[i++] = (byte) ((data.length >>> 8) & 0xFF);
        out[i++] = (byte) (data.length & 0xFF);
        // Данные (если есть)
        if (data.length > 0) {
            System.arraycopy(data, 0, out, i, data.length);
        }
        return out;
    }

    /**
     * Десериализует байтовый массив в объект UdpPacket.
     *
     * Выполняет валидацию: если длина входного буфера меньше размера
     * заголовка, выбрасывает IOException. Длина данных ограничивается
     * фактическим размером буфера для защиты от переполнения.
     *
     * @param buf входной байтовый массив
     * @param len фактическая длина полезных данных в буфере
     * @return новый экземпляр UdpPacket
     * @throws IOException если буфер слишком короткий для заголовка
     */
    public static UdpPacket fromBytes(byte[] buf, int len) throws IOException {
        if (len < Config.HEADER_SIZE) {
            throw new IOException("Некорректный пакет: длина меньше заголовка");
        }
        int i = 0;
        // Чтение полей заголовка (big-endian)
        int seq = ((buf[i++] & 0xFF) << 8) | (buf[i++] & 0xFF);
        int ack = ((buf[i++] & 0xFF) << 8) | (buf[i++] & 0xFF);
        byte flags = buf[i++];
        int dlen = ((buf[i++] & 0xFF) << 8) | (buf[i++] & 0xFF);
        // Защита: не читаем больше, чем реально есть в буфере
        dlen = Math.min(dlen, len - Config.HEADER_SIZE);
        byte[] d = dlen > 0 ? Arrays.copyOfRange(buf, i, i + dlen) : new byte[0];
        return new UdpPacket(seq, ack, flags, d);
    }
}