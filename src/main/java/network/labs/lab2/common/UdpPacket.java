package network.labs.lab2.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Бинарный пакет для надёжной передачи по UDP.
 *
 * Формат заголовка (7 байт):
 * ┌──────────┬──────────┬─────────┬──────────┐
 * │ Seq (2B) │ ACK (2B) │ Flags   │ DataLen  │
 * │          │          │ (1B)    │ (2B)     │
 * └──────────┴──────────┴─────────┴──────────┘
 * 2+2+1+2 = 7 байт
 *
 * Полезная нагрузка: до 1472 байт (данные или текстовая команда)
 *
 * Флаги (битовая маска):
 * - 0x01 (DATA): пакет содержит данные файла
 * - 0x02 (ACK): пакет является подтверждением
 * - 0x04 (CMD): пакет содержит текстовую команду
 * - 0x08 (FIN): конец передачи файла
 * - 0x10 (RESEND): запрос повторной отправки
 */
public class UdpPacket {
    private static final Logger log = LoggerFactory.getLogger(UdpPacket.class);

    // ========================================================================
    // Константы
    // ========================================================================

    /** Размер заголовка в байтах: 2+2+1+2 = 7 */
    private static final int HEADER_SIZE = 7;

    /** Пакет содержит данные файла */
    public static final byte FLAG_DATA = 0x01;

    /** Пакет является подтверждением (ACK) */
    public static final byte FLAG_ACK = 0x02;

    /** Пакет содержит текстовую команду */
    public static final byte FLAG_CMD = 0x04;

    /** Конец передачи файла */
    public static final byte FLAG_FIN = 0x08;

    /** Запрос повторной отправки */
    public static final byte FLAG_RESEND = 0x10;

    // ========================================================================
    // Поля пакета
    // ========================================================================

    /** Номер последовательности (0-65535) */
    private final int seqNum;

    /** Номер подтверждаемого пакета (0 если не ACK) */
    private final int ackNum;

    /** Флаги (битовая маска) */
    private final byte flags;

    /** Длина полезных данных (0-1472) */
    private final int dataLen;

    /** Полезные данные (команда или фрагмент файла) */
    private final byte[] data;

    // ========================================================================
    // Конструкторы
    // ========================================================================

    /**
     * Создаёт пакет с данными.
     *
     * @param seqNum номер последовательности
     * @param ackNum номер подтверждаемого пакета (0 если не ACK)
     * @param flags битовая маска флагов
     * @param data полезные данные (до 1472 байт)
     */
    public UdpPacket(int seqNum, int ackNum, byte flags, byte[] data) {
        this.seqNum = seqNum & 0xFFFF; // гарантируем 2 байта
        this.ackNum = ackNum & 0xFFFF;
        this.flags = flags;
        this.dataLen = data != null ? Math.min(data.length, Config.UDP_MAX_PAYLOAD) : 0;
        this.data = data != null && data.length > 0 ?
                Arrays.copyOf(data, this.dataLen) : new byte[0];
    }

    /**
     * Создаёт пакет-команду (текстовый).
     *
     * @param seqNum номер последовательности
     * @param command текстовая команда (кодировка UTF-8)
     * @return UdpPacket с флагом CMD
     */
    public static UdpPacket createCommand(int seqNum, String command) {
        byte[] cmdBytes = command.getBytes(StandardCharsets.UTF_8);
        return new UdpPacket(seqNum, 0, FLAG_CMD, cmdBytes);
    }

    /**
     * Создаёт пакет-подтверждение (ACK).
     *
     * @param ackSeq номер подтверждаемого пакета
     * @return UdpPacket с флагом ACK
     */
    public static UdpPacket createAck(int ackSeq) {
        return new UdpPacket(0, ackSeq, FLAG_ACK, new byte[0]);
    }

    /**
     * Создаёт пакет с данными файла.
     *
     * @param seqNum номер последовательности
     * @param fileData фрагмент файла (до 1472 байт)
     * @return UdpPacket с флагом DATA
     */
    public static UdpPacket createData(int seqNum, byte[] fileData) {
        return new UdpPacket(seqNum, 0, FLAG_DATA, fileData);
    }

    /**
     * Создаёт финальный пакет (конец передачи).
     *
     * @param seqNum номер последовательности
     * @return UdpPacket с флагом FIN
     */
    public static UdpPacket createFin(int seqNum) {
        return new UdpPacket(seqNum, 0, FLAG_FIN, new byte[0]);
    }

    // ========================================================================
    // Сериализация / десериализация
    // ========================================================================

    /**
     * Преобразует пакет в массив байт для отправки по сети.
     *
     * @return байтовый массив (заголовок 7 байт + данные)
     * @throws IOException если ошибка записи
     */
    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(HEADER_SIZE + dataLen);
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeShort(seqNum);
        dos.writeShort(ackNum);
        dos.writeByte(flags);
        dos.writeShort(dataLen);
        dos.write(data);

        byte[] result = baos.toByteArray();

        //  Дебаг: что отправляем
        log.debug("{}", debugDump("SEND", result, result.length));

        return result;
    }

    /**
     * Восстанавливает пакет из байтового массива с указанием длины.
     *
     * @param bytes полученный массив байт
     * @param length количество валидных байт в массиве (обычно packet.getLength())
     * @return объект UdpPacket
     * @throws IOException если пакет некорректный или слишком короткий
     */
    public static UdpPacket fromBytes(byte[] bytes, int length) throws IOException {
        if (length < HEADER_SIZE) {
            throw new IOException("Слишком короткий пакет: " + length + " байт (минимум " + HEADER_SIZE + ")");
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(bytes, 0, length);
        DataInputStream dis = new DataInputStream(bais);

        int seq = dis.readUnsignedShort();    // 2 байта
        int ack = dis.readUnsignedShort();    // 2 байта
        byte flags = dis.readByte();          // 1 байт
        int declaredDataLen = dis.readUnsignedShort(); // 2 байта

        // Вычисляем, сколько данных реально доступно
        int availableData = length - HEADER_SIZE;

        // Берём минимум из объявленной длины и доступной (защита от рассинхрона)
        int actualDataLen = Math.min(declaredDataLen, availableData);

        // Если объявленная длина больше доступной — логируем предупреждение
        if (declaredDataLen > availableData) {
            log.warn("UdpPacket: declared dataLen={} > available={}, усекаем",
                    declaredDataLen, availableData);
        }

        byte[] data = new byte[actualDataLen];
        if (actualDataLen > 0) {
            dis.readFully(data);
        }

        return new UdpPacket(seq, ack, flags, data);
    }

    // ========================================================================
    // Геттеры и утилиты
    // ========================================================================

    public int getSeqNum() { return seqNum; }
    public int getAckNum() { return ackNum; }
    public byte getFlags() { return flags; }
    public int getDataLen() { return dataLen; }
    public byte[] getData() { return Arrays.copyOf(data, dataLen); }

    public boolean hasFlag(byte flag) { return (flags & flag) != 0; }
    public boolean isData() { return hasFlag(FLAG_DATA); }
    public boolean isAck() { return hasFlag(FLAG_ACK); }
    public boolean isCommand() { return hasFlag(FLAG_CMD); }
    public boolean isFin() { return hasFlag(FLAG_FIN); }
    public boolean isResendRequest() { return hasFlag(FLAG_RESEND); }

    /**
     * Извлекает текстовую команду из пакета (если флаг CMD установлен).
     *
     * @return команда как строка UTF-8 или null если не команда
     */
    public String getCommand() {
        if (!isCommand() || dataLen == 0) {
            return null;
        }
        return new String(data, 0, dataLen, StandardCharsets.UTF_8);
    }

    // ========================================================================
    // Отладка
    // ========================================================================

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("UdpPacket{");
        sb.append("seq=").append(seqNum);
        if (ackNum > 0) sb.append(", ack=").append(ackNum);
        sb.append(", flags=0x").append(Integer.toHexString(flags & 0xFF));
        sb.append(", len=").append(dataLen);
        if (isCommand()) {
            String cmd = getCommand();
            if (cmd != null) {
                if (cmd.length() > 30) {
                    sb.append(", cmd=\"").append(cmd.substring(0, 30)).append("...\"");
                } else {
                    sb.append(", cmd=\"").append(cmd).append("\"");
                }
            }
        }
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UdpPacket udpPacket = (UdpPacket) o;
        if (seqNum != udpPacket.seqNum) return false;
        if (ackNum != udpPacket.ackNum) return false;
        if (flags != udpPacket.flags) return false;
        if (dataLen != udpPacket.dataLen) return false;
        return Arrays.equals(data, udpPacket.data);
    }

    @Override
    public int hashCode() {
        int result = seqNum;
        result = 31 * result + ackNum;
        result = 31 * result + (int) flags;
        result = 31 * result + dataLen;
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }


// ========================================================================
// Отладка: визуализация пакета
// ========================================================================

    /**
     * Возвращает детальное представление пакета для отладки.
     * Показывает заголовок, флаги и превью данных.
     *
     * @param prefix префикс для вывода (например, ">> SEND" или "<< RECV")
     * @param rawBytes сырые байты пакета
     * @param length реальная длина пакета
     * @return отформатированная строка для лога
     */
    private String debugDump(String prefix, byte[] rawBytes, int length) {
        if (length < HEADER_SIZE) {
            return String.format("%s [INVALID: too short %d bytes]", prefix, length);
        }

        // Парсим заголовок
        int seq = ((rawBytes[0] & 0xFF) << 8) | (rawBytes[1] & 0xFF);
        int ack = ((rawBytes[2] & 0xFF) << 8) | (rawBytes[3] & 0xFF);
        byte flags = rawBytes[4];
        int dataLen = ((rawBytes[5] & 0xFF) << 8) | (rawBytes[6] & 0xFF);

        // Формируем строку флагов
        StringBuilder flagStr = new StringBuilder("[");
        if ((flags & FLAG_CMD) != 0) flagStr.append("CMD ");
        if ((flags & FLAG_DATA) != 0) flagStr.append("DATA ");
        if ((flags & FLAG_ACK) != 0) flagStr.append("ACK ");
        if ((flags & FLAG_FIN) != 0) flagStr.append("FIN ");
        if ((flags & FLAG_RESEND) != 0) flagStr.append("RESEND ");
        if (flagStr.length() == 1) flagStr.append("NONE");
        flagStr.append("]");

        // Превью данных (первые 20 байт)
        int previewLen = Math.min(20, length - HEADER_SIZE);
        StringBuilder dataPreview = new StringBuilder();
        for (int i = 0; i < previewLen; i++) {
            byte b = rawBytes[HEADER_SIZE + i];
            if (b >= 32 && b < 127) {
                dataPreview.append((char) b);
            } else {
                dataPreview.append('.');
            }
        }
        if (length - HEADER_SIZE > previewLen) {
            dataPreview.append("...");
        }

        // Hex-дампер первых 16 байт заголовка + данных
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < Math.min(16, length); i++) {
            hex.append(String.format("%02X ", rawBytes[i]));
        }

        return String.format("%s | seq=%d ack=%d %s len=%d | \"%s\" | hex: %s",
                prefix, seq, ack, flagStr.toString(), dataLen,
                dataPreview.toString().trim(), hex.toString().trim());
    }
}