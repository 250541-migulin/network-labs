package network.labs.lab2.core;

import java.io.IOException;

public interface CommandContextUdp {
    void writeLine(String line) throws IOException;
    String readLine() throws IOException;
}