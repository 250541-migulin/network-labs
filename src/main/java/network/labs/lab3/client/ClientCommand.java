package network.labs.lab3.client;

import java.io.IOException;

public interface ClientCommand {
    String name();
    void execute(String[] args, ClientContext ctx) throws IOException;
}
