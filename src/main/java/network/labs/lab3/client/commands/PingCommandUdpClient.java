package network.labs.lab3.client.commands;

import network.labs.lab3.client.*;
import java.io.IOException;

public class PingCommandUdpClient implements UdpClientCommand {
    @Override
    public String name() { return "PING"; }

    @Override
    public void execute(String[] args, UdpClientContext ctx) throws IOException {
        ctx.sendLine("PING");
        System.out.println("➡️ отправлено: PING");
        System.out.println("⬅️ ответ: " + ctx.readLine());
    }
}
