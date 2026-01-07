package network.labs.lab1.server.commands;

import network.labs.lab1.common.*;
import java.io.IOException;

public class CloseCommand implements Command<CommandContext> {
    @Override
    public String name() {
        return CommandName.CLOSE.key();
    }

    @Override
    public CommandResult execute(String[] args, CommandContext ctx) throws IOException {
        ctx.writeLine("Соединение закрыто по запросу");
        return CommandResult.CLOSE;
    }
}