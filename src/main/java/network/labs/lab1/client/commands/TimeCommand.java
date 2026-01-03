package network.labs.lab1.client.commands;

import network.labs.lab1.common.*;
import network.labs.lab1.client.ClientCommandContext;

import java.io.IOException;

/**
 * Команда запроса текущего времени у сервера.
 */
public class TimeCommand implements Command<ClientCommandContext> {
    @Override
    public String name() {
        return CommandName.TIME.key();
    }

    @Override
    public CommandResult execute(String[] args, ClientCommandContext ctx) throws IOException {
        IoUtils.writeLine(ctx.out(), CommandName.TIME.key());
        String response = IoUtils.readLine(ctx.in());
        System.out.println("Сервер: " + response);
        return CommandResult.CONTINUE;
    }
}
