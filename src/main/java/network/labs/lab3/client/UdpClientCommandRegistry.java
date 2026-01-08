package network.labs.lab3.client;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class UdpClientCommandRegistry {
    private final Map<String, UdpClientCommand> commands = new HashMap<>();
    private final UdpClientCommand unknown = new UnknownUdpClientCommand();

    public void register(UdpClientCommand cmd) {
        commands.put(cmd.name().toUpperCase(), cmd);
    }

    public void dispatch(String line, UdpClientContext ctx) throws IOException {
        if (line == null || line.isBlank()) return;
        String[] parts = line.trim().split("\\s+");
        String cmdName = parts[0].toUpperCase();
        UdpClientCommand cmd = commands.getOrDefault(cmdName, unknown);
        cmd.execute(parts, ctx);
    }
}
