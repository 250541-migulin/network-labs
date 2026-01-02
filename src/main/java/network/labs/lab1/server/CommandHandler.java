package network.labs.lab1.server;

/**
 * Обработчик команды.
 * Выполняет действие на основе аргументов и контекста клиента.
 */
@FunctionalInterface
public interface CommandHandler {
    /**
     * Выполнение команды.
     *
     * @param args аргументы команды
     * @param ctx контекст клиента (потоки и сокет)
     * @return true если после выполнения нужно закрыть соединение
     */
    boolean handle(String[] args, CommandContext ctx);
}
