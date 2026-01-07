package network.labs.lab1.server.commands;

import network.labs.lab1.common.*;
import java.io.IOException;

/**
 * Серверная команда ECHO: возвращает аргументы как есть.
 */
public class EchoCommand implements Command<CommandContext> {
    @Override
    public String name() {
        return CommandName.ECHO.key();
    }

    @Override
    public CommandResult execute(String[] args, CommandContext ctx) throws IOException {
        ctx.writeLine(String.join(" ", args));
        return CommandResult.CONTINUE;
    }
}