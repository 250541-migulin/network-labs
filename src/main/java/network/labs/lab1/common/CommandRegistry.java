package network.labs.lab1.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class CommandRegistry<C extends CommandContext> {
    private static final Logger log = LoggerFactory.getLogger(CommandRegistry.class);
    private final Map<String, Command<C>> commands = new HashMap<>();

    public void register(Command<C> cmd) {
        commands.put(cmd.name().toUpperCase(), cmd);
    }

    public CommandResult dispatch(String line, C ctx) throws IOException {
        String[] parts = line.trim().split("\\s+", 2);
        String cmdName = parts.length > 0 ? parts[0].toUpperCase() : "";
        String argsLine = parts.length > 1 ? parts[1] : "";

        Command<C> cmd = commands.get(cmdName);
        if (cmd == null) {
            cmd = commands.get(CommandName.UNKNOWN.key());
            if (cmd == null) {
                ctx.writeLine("ОШИБКА: неизвестная команда");
                return CommandResult.ERROR;
            }
            return cmd.execute(new String[]{line}, ctx);
        }

        String[] args = argsLine.isEmpty() ? new String[0] : argsLine.split("\\s+");
        return cmd.execute(args, ctx);
    }
}