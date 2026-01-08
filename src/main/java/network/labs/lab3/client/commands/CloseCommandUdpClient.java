package network.labs.lab3.client.commands;

import network.labs.lab3.client.*;
import java.io.IOException;

public class CloseCommandUdpClient implements UdpClientCommand {
    @Override
    public String name() { return "CLOSE"; }

    @Override
    public void execute(String[] args, UdpClientContext ctx) throws IOException {
        ctx.sendLine("CLOSE");
        System.out.println("➡️ отправлено: CLOSE");
        System.out.println("⬅️ ответ: " + ctx.readLine());
        ctx.close();
        System.out.println("🔗 соединение закрыто");
        System.exit(0);
    }
}
