package network.labs.lab3.server.commands;

import network.labs.lab3.server.*;

import java.io.IOException;

public class UnknownCommandTcp implements Command<TcpNioContext> {
    @Override
    public String name() { return "UNKNOWN"; }

    @Override
    public void execute(String[] args, TcpNioContext ctx) throws IOException {
        ctx.writeLine("TCP: Unknown command");
    }
}
