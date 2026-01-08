package network.labs.lab3.server.commands;

import network.labs.lab3.server.*;

import java.io.IOException;
import java.time.LocalDateTime;

public class TimeCommandTcp implements Command<TcpNioContext> {
    @Override
    public String name() { return "TIME"; }

    @Override
    public void execute(String[] args, TcpNioContext ctx) throws IOException {
        ctx.writeLine("TCP TIME: " + LocalDateTime.now());
    }
}
