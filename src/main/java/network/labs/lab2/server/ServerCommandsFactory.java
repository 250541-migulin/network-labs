package network.labs.lab2.server;

import network.labs.lab2.core.UdpCommandRegistry;
import network.labs.lab2.server.commands.*;
import java.nio.file.Path;

public final class ServerCommandsFactory {
    private ServerCommandsFactory() {}

    public static UdpCommandRegistry create(Path serverDir) {
        UnknownCommandUdp unknown = new UnknownCommandUdp();
        UdpCommandRegistry reg = new UdpCommandRegistry(unknown);

        reg.register(new EchoCommandUdp());
        reg.register(new TimeCommandUdp());
        reg.register(new CloseCommandUdp());

        reg.register(new UploadCommandUdp(serverDir));
        reg.register(new DownloadCommandUdp(serverDir));

        return reg;
    }
}
