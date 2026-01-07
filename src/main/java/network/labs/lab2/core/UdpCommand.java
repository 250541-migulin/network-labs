package network.labs.lab2.core;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

public interface UdpCommand {
    String name();
    void execute(String[] args, DatagramSocket socket, InetSocketAddress peer) throws IOException;
}
