package network.labs.lab1.client.commands;

import network.labs.lab1.common.*;

import java.io.IOException;

/**
 * Клиентская команда ECHO: отправляет и выводит ответ.
 */
public class EchoCommand implements Command<CommandContext> {
    @Override
    public String name() {
        return CommandName.ECHO.key();
    }

    @Override
    public CommandResult execute(String[] args, CommandContext ctx) throws IOException {
        ctx.writeLine(CommandName.ECHO.key() + " " + String.join(" ", args));
        String response = ctx.readLine();
        System.out.println("Сервер: " + response);
        return CommandResult.CONTINUE;
    }
}