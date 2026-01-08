package network.labs.lab3.server.commands;

import network.labs.lab3.server.*;

import java.io.IOException;

public class EchoCommandTcp implements Command<TcpNioContext> {
    @Override
    public String name() { return "ECHO"; }

    @Override
    public void execute(String[] args, TcpNioContext ctx) throws IOException {
        String msg = String.join(" ", args).replaceFirst("ECHO", "").trim();
        ctx.writeLine("TCP ECHO: " + msg);
    }
}
