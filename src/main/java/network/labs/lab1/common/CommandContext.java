package network.labs.lab1.common;

import java.io.IOException;

/**
 * Базовый интерфейс для выполнения команд.
 * Абстрагирует транспорт (TCP/UDP).
 */
public interface CommandContext {
    void writeLine(String line) throws IOException;
    String readLine() throws IOException;
}