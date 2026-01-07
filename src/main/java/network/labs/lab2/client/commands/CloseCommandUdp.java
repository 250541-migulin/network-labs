package network.labs.lab2.client.commands;

import network.labs.lab2.core.UdpCommand;
import network.labs.lab2.util.UdpIo;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

public class CloseCommandUdp implements UdpCommand {
    @Override public String name() { return "CLOSE"; }

    @Override
    public void execute(String[] args, DatagramSocket socket, InetSocketAddress server) throws IOException {
        UdpIo.sendLine(socket, server, "CLOSE");
        System.out.println("Сервер: " + UdpIo.receiveLine(socket));
        // при желании клиент может завершить работу после этого
    }
}
