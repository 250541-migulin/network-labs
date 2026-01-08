package network.labs.lab3.server.commands;

import network.labs.lab3.server.*;

import java.io.IOException;
import java.time.LocalDateTime;

public class TimeCommandUdp implements Command<UdpNioContext> {
    @Override
    public String name() { return "TIME"; }

    @Override
    public void execute(String[] args, UdpNioContext ctx) throws IOException {
        ctx.sendLine("UDP TIME: " + LocalDateTime.now());
    }
}
