package network.labs.lab2.client.commands;

import network.labs.lab2.core.UdpCommand;
import network.labs.lab2.util.UdpIo;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

public class TimeCommandUdp implements UdpCommand {
    @Override public String name() { return "TIME"; }

    @Override
    public void execute(String[] args, DatagramSocket socket, InetSocketAddress server) throws IOException {
        UdpIo.sendLine(socket, server, "TIME");
        System.out.println("Сервер: " + UdpIo.receiveLine(socket));
    }
}
