package network.labs.lab1.client.commands;

import network.labs.lab1.client.ClientCommandContext;
import network.labs.lab1.common.Command;
import network.labs.lab1.common.CommandName;
import network.labs.lab1.common.CommandResult;
import network.labs.lab1.common.IoUtils;

import java.io.IOException;

public class UnknownCommand implements Command<ClientCommandContext> {
    @Override
    public String name() {
        return CommandName.UNKNOWN.key();
    }

    @Override
    public CommandResult execute(String[] args, ClientCommandContext ctx) throws IOException {
        String line = CommandName.UNKNOWN.key() + " " + String.join(" ", args);

        IoUtils.writeLine(ctx.out(), line);
        String response = IoUtils.readLine(ctx.in());
        System.out.println("Сервер: " + response);

        return CommandResult.CONTINUE;
    }
}

