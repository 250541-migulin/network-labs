package network.labs.lab2.server.commands;

import network.labs.lab2.core.UdpCommand;
import network.labs.lab2.util.UdpIo;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

public class CloseCommandUdp implements UdpCommand {
    @Override public String name() { return "CLOSE"; }
    @Override public void execute(String[] args, DatagramSocket socket, InetSocketAddress peer) throws IOException {
        UdpIo.sendLine(socket, peer, "Соединение закрыто");
        // UDP не держит соединений: семантика подтверждения.
    }
}
