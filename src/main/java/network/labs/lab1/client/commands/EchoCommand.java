package network.labs.lab1.client.commands;

import network.labs.lab1.common.*;
import network.labs.lab1.client.ClientCommandContext;

import java.io.IOException;

/**
 * Команда эхо: отправляет строку на сервер и выводит ответ.
 */
public class EchoCommand implements Command<ClientCommandContext> {
    @Override
    public String name() {
        return CommandName.ECHO.key();
    }

    @Override
    public CommandResult execute(String[] args, ClientCommandContext ctx) throws IOException {
        IoUtils.writeLine(ctx.out(), CommandName.ECHO.key() + " " + String.join(" ", args));
        String response = IoUtils.readLine(ctx.in());
        System.out.println("Сервер: " + response);
        return CommandResult.CONTINUE;
    }
}
