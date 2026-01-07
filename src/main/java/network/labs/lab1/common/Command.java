package network.labs.lab1.common;

import java.io.IOException;

public interface Command<C> {
    String name();
    CommandResult execute(String[] args, C ctx) throws IOException;
}