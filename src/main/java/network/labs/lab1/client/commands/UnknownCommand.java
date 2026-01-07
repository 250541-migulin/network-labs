package network.labs.lab1.client.commands;

import network.labs.lab1.common.*;

import java.io.IOException;

public class UnknownCommand implements Command<CommandContext> {
    @Override
    public String name() {
        return CommandName.UNKNOWN.key();
    }

    @Override
    public CommandResult execute(String[] args, CommandContext ctx) throws IOException {
        String line = CommandName.UNKNOWN.key() + " " + String.join(" ", args);
        ctx.writeLine(line);
        String response = ctx.readLine();
        System.out.println("Сервер: " + response);
        return CommandResult.CONTINUE;
    }
}