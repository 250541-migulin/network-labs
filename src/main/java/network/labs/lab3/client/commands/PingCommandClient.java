package network.labs.lab3.client.commands;

import network.labs.lab3.client.*;
import java.io.IOException;

public class PingCommandClient implements ClientCommand {
    @Override
    public String name() { return "PING"; }

    @Override
    public void execute(String[] args, ClientContext ctx) throws IOException {
        ctx.sendLine("PING");
        System.out.println("➡️ отправлено: PING");
        System.out.println("⬅️ ответ: " + ctx.readLine());
    }
}
