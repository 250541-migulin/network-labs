package network.labs.lab3.server;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CommandRegistry<C> {
    private final Map<String, Command<C>> commands = new HashMap<>();
    private final Command<C> unknown;

    public CommandRegistry(Command<C> unknown) {
        this.unknown = unknown;
    }

    public void register(Command<C> cmd) {
        commands.put(cmd.name().toUpperCase(), cmd);
    }

    public void dispatch(String line, C ctx) throws IOException {
        if (line == null || line.isBlank()) return;
        String[] parts = line.trim().split("\\s+");
        String cmdName = parts[0].toUpperCase();
        Command<C> cmd = commands.getOrDefault(cmdName, unknown);
        cmd.execute(parts, ctx);
    }
}
