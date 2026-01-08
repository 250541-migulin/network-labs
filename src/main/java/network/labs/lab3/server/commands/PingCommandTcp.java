package network.labs.lab3.server.commands;

import network.labs.lab3.server.*;

import java.io.IOException;

public class PingCommandTcp implements Command<TcpNioContext> {
    @Override
    public String name() { return "PING"; }

    @Override
    public void execute(String[] args, TcpNioContext ctx) throws IOException {
        ctx.writeLine("TCP PONG");
    }
}
