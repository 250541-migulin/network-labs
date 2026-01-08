package network.labs.lab3.server.commands;

import network.labs.lab3.server.*;

import java.io.IOException;

public class UnknownCommandUdp implements Command<UdpNioContext> {
    @Override
    public String name() { return "UNKNOWN"; }

    @Override
    public void execute(String[] args, UdpNioContext ctx) throws IOException {
        ctx.sendLine("UDP: Unknown command");
    }
}
