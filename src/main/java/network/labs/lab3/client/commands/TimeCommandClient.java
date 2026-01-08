package network.labs.lab3.client.commands;

import network.labs.lab3.client.*;
import java.io.IOException;

public class TimeCommandClient implements ClientCommand {
    @Override
    public String name() { return "TIME"; }

    @Override
    public void execute(String[] args, ClientContext ctx) throws IOException {
        ctx.sendLine("TIME");
        System.out.println("➡️ отправлено: TIME");
        System.out.println("⬅️ ответ: " + ctx.readLine());
    }
}
