package network.labs.lab3.server;

import java.io.IOException;

public interface Command<C> {
    String name();
    void execute(String[] args, C ctx) throws IOException;
}
