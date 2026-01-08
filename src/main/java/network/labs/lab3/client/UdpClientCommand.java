package network.labs.lab3.client;

import java.io.IOException;

public interface UdpClientCommand {
    String name();
    void execute(String[] args, UdpClientContext ctx) throws IOException;
}
