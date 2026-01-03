package network.labs.lab1.common;

import java.io.IOException;

/**
 * Универсальный интерфейс команды.
 * @param <C> тип контекста (серверный или клиентский)
 */
public interface Command<C> {
    String name();
    CommandResult execute(String[] args, C ctx) throws IOException;
}
