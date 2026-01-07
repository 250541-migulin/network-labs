package network.labs.lab1.server.commands;

import network.labs.lab1.common.*;
import java.io.IOException;

public class UnknownCommand implements Command<CommandContext> {
    @Override
    public String name() {
        return CommandName.UNKNOWN.key();
    }

    @Override
    public CommandResult execute(String[] args, CommandContext ctx) throws IOException {
        ctx.writeLine("ОШИБКА: неизвестная команда");
        return CommandResult.CONTINUE;
    }
}