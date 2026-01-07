package network.labs.lab2.server.commands;

import network.labs.lab2.core.UdpCommand;
import network.labs.lab2.util.UdpIo;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

public class EchoCommandUdp implements UdpCommand {
    @Override public String name() { return "ECHO"; }
    @Override public void execute(String[] args, DatagramSocket socket, InetSocketAddress peer) throws IOException {
        if (args.length == 0) {
            UdpIo.sendLine(socket, peer, "CTRL:ERROR Использование: ECHO <сообщение>");
            return;
        }
        String msg = String.join(" ", args);
        UdpIo.sendLine(socket, peer, "Эхо: " + msg);
    }
}
