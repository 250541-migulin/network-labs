package network.labs.lab3.server.commands;

import network.labs.lab3.server.*;

import java.io.IOException;

public class EchoCommandUdp implements Command<UdpNioContext> {
    @Override
    public String name() { return "ECHO"; }

    @Override
    public void execute(String[] args, UdpNioContext ctx) throws IOException {
        String msg = String.join(" ", args).replaceFirst("ECHO", "").trim();
        ctx.sendLine("UDP ECHO: " + msg);
    }
}
