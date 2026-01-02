package network.labs.lab1.server;

/**
 * Спецификация команды: хранит допустимое количество аргументов
 * и обработчик, выполняющий команду.
 */
public record CommandSpec(
        int minArgs,
        int maxArgs,
        CommandHandler handler
) {
    public boolean validate(String[] args) {
        return args.length >= minArgs && args.length <= maxArgs;
    }
}
