package network.labs.lab3.server.commands;

import network.labs.lab3.server.*;

import java.io.IOException;

public class PingCommandUdp implements Command<UdpNioContext> {
    @Override
    public String name() { return "PING"; }

    @Override
    public void execute(String[] args, UdpNioContext ctx) throws IOException {
        ctx.sendLine("UDP PONG");
    }
}
