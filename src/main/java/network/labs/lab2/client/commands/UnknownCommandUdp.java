package network.labs.lab2.client.commands;

import network.labs.lab2.core.UdpCommand;
import network.labs.lab2.util.UdpIo;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

public class UnknownCommandUdp implements UdpCommand {
    @Override public String name() { return "UNKNOWN"; }
    @Override public void execute(String[] args, DatagramSocket socket, InetSocketAddress server) throws IOException {
        String cmd = String.join(" ", args);
        UdpIo.sendLine(socket, server, cmd);
        System.out.println("Сервер: " + UdpIo.receiveLine(socket));
    }
}
