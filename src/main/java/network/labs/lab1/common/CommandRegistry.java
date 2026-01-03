package network.labs.lab1.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Реестр команд: хранит зарегистрированные команды и выполняет их по имени.
 *
 * @param <C> тип контекста (например, ServerCommandContext)
 */
public class CommandRegistry<C> {
    private final Map<String, Command<C>> commands = new HashMap<>();

    /**
     * Регистрирует команду в реестре.
     *
     * @param cmd команда
     */
    public void register(Command<C> cmd) {
        commands.put(cmd.name().toUpperCase(), cmd);
    }

    /**
     * Выполняет команду по строке ввода.
     *
     * @param line строка, введённая клиентом
     * @param ctx  контекст выполнения
     * @return результат выполнения команды
     * @throws IOException при ошибке ввода-вывода
     */
    public CommandResult dispatch(String line, C ctx) throws IOException {
        String[] parts = line.trim().split("\\s+");     // первое слово команда, остальные аргументы
        if (parts.length == 0) {
            return CommandResult.ERROR;
        }

        String cmdName = parts[0].toUpperCase();
        Command<C> cmd = commands.getOrDefault(cmdName, commands.get(CommandName.UNKNOWN.key()));

        String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        return cmd.execute(args, ctx);
    }
}
