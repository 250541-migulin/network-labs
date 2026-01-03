package network.labs.lab1.server.commands;

import network.labs.lab1.common.*;
import network.labs.lab1.server.ServerCommandContext;

import java.io.IOException;

/**
 * Команда эхо: возвращает клиенту ту же строку.
 */
public class EchoCommand implements Command<ServerCommandContext> {
    @Override
    public String name() {
        return CommandName.ECHO.key();
    }

    @Override
    public CommandResult execute(String[] args, ServerCommandContext ctx) throws IOException {
        ctx.writeLine(args.length == 0 ? "" : String.join(" ", args));
        return CommandResult.CONTINUE;
    }
}
