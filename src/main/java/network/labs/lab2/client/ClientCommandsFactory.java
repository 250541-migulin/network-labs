package network.labs.lab2.client;

import network.labs.lab2.core.UdpCommandRegistry;
import network.labs.lab2.client.commands.*;

public final class ClientCommandsFactory {
    private ClientCommandsFactory() {}

    public static UdpCommandRegistry create() {
        UnknownCommandUdp unknown = new UnknownCommandUdp();
        UdpCommandRegistry reg = new UdpCommandRegistry(unknown);

        reg.register(new EchoCommandUdp());
        reg.register(new TimeCommandUdp());
        reg.register(new CloseCommandUdp());

        reg.register(new UploadCommandUdp());
        reg.register(new DownloadCommandUdp());

        return reg;
    }
}
