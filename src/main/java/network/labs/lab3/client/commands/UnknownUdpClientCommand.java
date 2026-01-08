package network.labs.lab3.client;

import java.io.IOException;

public class UnknownUdpClientCommand implements UdpClientCommand {
    @Override
    public String name() { return "UNKNOWN"; }

    @Override
    public void execute(String[] args, UdpClientContext ctx) throws IOException {
        System.out.println("❌ Неизвестная команда");
    }
}
