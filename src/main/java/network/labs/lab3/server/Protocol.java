package network.labs.lab3.server;

/**
 * Общие команды и служебные маркеры для протокола Lab2/Lab3.
 * Используются и TCP, и UDP серверами/клиентами.
 */
public final class Protocol {
    private Protocol() {}

    // Команды
    public static final String CMD_TIME     = "TIME";
    public static final String CMD_PING     = "PING";
    public static final String CMD_UPLOAD   = "UPLOAD";   // UPLOAD <filename> <size>
    public static final String CMD_DOWNLOAD = "DOWNLOAD"; // DOWNLOAD <filename>
    public static final String CMD_ECHO     = "ECHO";
    public static final String CMD_CLOSE = "CLOSE";


    // Служебные ответы
    public static final String READY   = "READY"; // сервер готов к передаче/приёму
    public static final String END     = "END";   // конец передачи (UDP)
    public static final String DONE    = "DONE";  // конец передачи (TCP)
    public static final String ERROR   = "ERROR"; // ошибка выполнения команды

    // Разделитель строк
    public static final String CRLF = "\r\n";

    // Ограничения по размеру порции
    public static final int TCP_CHUNK_SIZE = 8 * 1024;
    public static final int UDP_CHUNK_SIZE = 1300; // безопасно для MTU ~1500
}
