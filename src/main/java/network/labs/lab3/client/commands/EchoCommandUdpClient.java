package network.labs.lab3.client.commands;

import network.labs.lab3.client.*;
import java.io.IOException;

public class EchoCommandUdpClient implements UdpClientCommand {
    @Override
    public String name() { return "ECHO"; }

    @Override
    public void execute(String[] args, UdpClientContext ctx) throws IOException {
        if (args.length < 2) {
            System.out.println("❌ нужно указать сообщение: ECHO <text>");
            return;
        }
        String msg = String.join(" ", args).replaceFirst("ECHO", "").trim();
        ctx.sendLine("ECHO " + msg);
        System.out.println("➡️ отправлено: ECHO " + msg);
        System.out.println("⬅️ ответ: " + ctx.readLine());
    }
}
