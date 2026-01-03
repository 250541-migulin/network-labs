package network.labs.lab1.server.commands;

import network.labs.lab1.common.*;
import network.labs.lab1.server.ServerCommandContext;

import java.io.IOException;

/**
 * Команда CLOSE: закрывает соединение с клиентом.
 */
public class CloseCommand implements Command<ServerCommandContext> {
    @Override
    public String name() {
        return CommandName.CLOSE.key();
    }

    @Override
    public CommandResult execute(String[] args, ServerCommandContext ctx) throws IOException {
        ctx.writeLine("Соединение закрыто сервером");
        return CommandResult.CLOSE;
    }
}
