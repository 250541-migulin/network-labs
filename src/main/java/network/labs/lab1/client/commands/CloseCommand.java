package network.labs.lab1.client.commands;

import network.labs.lab1.common.*;
import network.labs.lab1.client.ClientCommandContext;

import java.io.IOException;

/**
 * Команда закрытия соединения.
 */
public class CloseCommand implements Command<ClientCommandContext> {
    @Override
    public String name() {
        return CommandName.CLOSE.key();
    }

    @Override
    public CommandResult execute(String[] args, ClientCommandContext ctx) throws IOException {
        IoUtils.writeLine(ctx.out(), CommandName.CLOSE.key());
        String response = IoUtils.readLine(ctx.in());
        System.out.println("Сервер: " + response);
        return CommandResult.CLOSE;
    }
}
