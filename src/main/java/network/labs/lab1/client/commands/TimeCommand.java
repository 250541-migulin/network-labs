package network.labs.lab1.client.commands;

import network.labs.lab1.common.*;

import java.io.IOException;

public class TimeCommand implements Command<CommandContext> {
    @Override
    public String name() {
        return CommandName.TIME.key();
    }

    @Override
    public CommandResult execute(String[] args, CommandContext ctx) throws IOException {
        ctx.writeLine(CommandName.TIME.key());
        String response = ctx.readLine();
        System.out.println("Сервер: " + response);
        return CommandResult.CONTINUE;
    }
}