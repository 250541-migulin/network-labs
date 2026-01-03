package network.labs.lab1.common;

/** Результат выполнения команды. */
public enum CommandResult {
    CONTINUE, // команда выполнена, соединение остаётся
    CLOSE,    // команда требует закрыть соединение
    ERROR     // ошибка выполнения
}
