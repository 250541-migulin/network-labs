package network.labs.lab3.server;

import network.labs.lab3.server.commands.*;

import java.nio.file.Path;

/**
 * Фабрика команд для Lab3.
 * TCP → команды для потокового ввода/вывода
 * UDP → команды для пакетного ввода/вывода
 */
public final class ServerCommandsFactory {
    private ServerCommandsFactory() {}

    /** Реестр TCP-команд */
    public static CommandRegistry<TcpNioContext> createTcpTextRegistry() {
        CommandRegistry<TcpNioContext> reg = new CommandRegistry<>(new UnknownCommandTcp());
        reg.register(new EchoCommandTcp());
        reg.register(new TimeCommandTcp());
        reg.register(new PingCommandTcp());
        reg.register(new CloseCommandTcp());
        return reg;
    }

    public static CommandRegistry<TcpNioContext> createTcpFileRegistry(Path serverDir) {
        CommandRegistry<TcpNioContext> reg = new CommandRegistry<>(new UnknownCommandTcp());
        reg.register(new UploadCommandTcp(serverDir));
        reg.register(new DownloadCommandTcp(serverDir));
        return reg;
    }

    /** Реестр UDP-команд */
    public static CommandRegistry<UdpNioContext> createUdpRegistry(Path serverDir) {
        CommandRegistry<UdpNioContext> reg = new CommandRegistry<>(new UnknownCommandUdp());
        reg.register(new EchoCommandUdp());
        reg.register(new TimeCommandUdp());
        reg.register(new PingCommandUdp());
        reg.register(new CloseCommandUdp());
        reg.register(new UploadCommandUdp(serverDir));
        reg.register(new DownloadCommandUdp(serverDir));
        return reg;
    }
}
