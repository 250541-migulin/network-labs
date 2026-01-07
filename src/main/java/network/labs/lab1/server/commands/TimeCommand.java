package network.labs.lab1.server.commands;

import network.labs.lab1.common.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TimeCommand implements Command<CommandContext> {
    @Override
    public String name() {
        return CommandName.TIME.key();
    }

    @Override
    public CommandResult execute(String[] args, CommandContext ctx) throws IOException {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        ctx.writeLine("Текущее время: " + now);
        return CommandResult.CONTINUE;
    }
}