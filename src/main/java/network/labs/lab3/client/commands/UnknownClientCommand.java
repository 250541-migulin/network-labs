package network.labs.lab3.client;

import java.io.IOException;

public class UnknownClientCommand implements ClientCommand {
    @Override
    public String name() { return "UNKNOWN"; }

    @Override
    public void execute(String[] args, ClientContext ctx) throws IOException {
        System.out.println("❌ Неизвестная команда");
    }
}
