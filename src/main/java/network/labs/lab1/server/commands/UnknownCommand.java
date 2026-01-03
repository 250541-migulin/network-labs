package network.labs.lab1.server.commands;

import network.labs.lab1.common.Command;
import network.labs.lab1.common.CommandName;
import network.labs.lab1.common.CommandResult;
import network.labs.lab1.server.ServerCommandContext;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnknownCommand implements Command<ServerCommandContext> {
    private static final Logger log = LoggerFactory.getLogger(UnknownCommand.class);

    @Override
    public String name() {
        return CommandName.UNKNOWN.key();
    }

    @Override
    public CommandResult execute(String[] args, ServerCommandContext ctx) throws IOException {
        ctx.writeLine("ОШИБКА: неизвестная команда");
        log.warn("Неизвестная команда от клиента: {}", String.join(" ", args));
        return CommandResult.CONTINUE;
    }
}


