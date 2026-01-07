package network.labs.lab2.core;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

public class UdpCommandRegistry {
    private final Map<String, UdpCommand> commands = new HashMap<>();
    private final UdpCommand unknown;

    public UdpCommandRegistry(UdpCommand unknown) {
        this.unknown = unknown;
    }

    public void register(UdpCommand cmd) {
        commands.put(cmd.name().toUpperCase(), cmd);
    }

    public void dispatch(String line, DatagramSocket socket, InetSocketAddress peer) throws IOException {
        String[] parts = line.trim().split("\\s+");
        String cmdName = parts[0].toUpperCase();
        String[] args = parts.length > 1 ? java.util.Arrays.copyOfRange(parts, 1, parts.length) : new String[0];

        UdpCommand cmd = commands.getOrDefault(cmdName, unknown);
        cmd.execute(args, socket, peer);
    }
}
