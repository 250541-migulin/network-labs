package network.labs.lab3.client;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ClientCommandRegistry {
    private final Map<String, ClientCommand> commands = new HashMap<>();
    private final ClientCommand unknown = new UnknownClientCommand();

    public void register(ClientCommand cmd) {
        commands.put(cmd.name().toUpperCase(), cmd);
    }

    public void dispatch(String line, ClientContext ctx) throws IOException {
        if (line == null || line.isBlank()) return;
        String[] parts = line.trim().split("\\s+");
        String cmdName = parts[0].toUpperCase();
        ClientCommand cmd = commands.getOrDefault(cmdName, unknown);
        cmd.execute(parts, ctx);
    }
}
