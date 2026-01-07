package network.labs.lab2.server.commands;

import network.labs.lab2.core.UdpCommand;
import network.labs.lab2.util.UdpIo;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TimeCommandUdp implements UdpCommand {
    @Override public String name() { return "TIME"; }
    @Override public void execute(String[] args, DatagramSocket socket, InetSocketAddress peer) throws IOException {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        UdpIo.sendLine(socket, peer, "Текущее время: " + now);
    }
}
