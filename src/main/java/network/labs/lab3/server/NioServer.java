package network.labs.lab3.server;

import network.labs.lab2.util.PathsConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Path;
import java.util.Iterator;

public class NioServer {
    private final int tcpPort;
    private final int udpPort;
    private final Path tcpDir;
    private final Path udpDir;

    public NioServer(int tcpPort, int udpPort, Path tcpDir, Path udpDir) {
        this.tcpPort = tcpPort;
        this.udpPort = udpPort;
        this.tcpDir = tcpDir;
        this.udpDir = udpDir;
    }

    public void start() throws IOException {
        Selector selector = Selector.open();

        // TCP сервер
        ServerSocketChannel tcpServer = ServerSocketChannel.open();
        tcpServer.configureBlocking(false);
        tcpServer.bind(new InetSocketAddress(tcpPort));
        tcpServer.register(selector, SelectionKey.OP_ACCEPT);

        // UDP сервер
        DatagramChannel udpServer = DatagramChannel.open();
        udpServer.configureBlocking(false);
        udpServer.bind(new InetSocketAddress(udpPort));
        udpServer.register(selector, SelectionKey.OP_READ);

        // Реестры команд
        var tcpTextReg = ServerCommandsFactory.createTcpTextRegistry();
        var tcpFileReg = ServerCommandsFactory.createTcpFileRegistry(tcpDir);
        var udpReg = ServerCommandsFactory.createUdpRegistry(udpDir);

        ByteBuffer buf = ByteBuffer.allocate(8192);

        System.out.println("🚀 NIO-сервер запущен: TCP=" + tcpPort + ", UDP=" + udpPort);

        while (true) {
            selector.select();
            Iterator<SelectionKey> it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove();

                if (!key.isValid()) continue;

                // TCP: новое подключение
                if (key.isAcceptable()) {
                    SocketChannel ch = tcpServer.accept();
                    ch.configureBlocking(false);
                    ch.register(selector, SelectionKey.OP_READ);
                    System.out.println("🔗 TCP-клиент подключился: " + ch.getRemoteAddress());
                }

                // TCP: чтение
                if (key.isReadable() && key.channel() instanceof SocketChannel) {
                    SocketChannel ch = (SocketChannel) key.channel();
                    TcpNioContext ctx = new TcpNioContext(ch, tcpDir);

                    String line = ctx.readLine();
                    if (line == null) {
                        ch.close();
                        continue;
                    }
                    if (line.isEmpty()) continue;

                    String cmd = line.split("\\s+")[0].toUpperCase();
                    if (Protocol.CMD_UPLOAD.equals(cmd) || Protocol.CMD_DOWNLOAD.equals(cmd)) {
                        tcpFileReg.dispatch(line, ctx);
                    } else {
                        tcpTextReg.dispatch(line, ctx);
                    }
                }

                // UDP: чтение
                if (key.isReadable() && key.channel() instanceof DatagramChannel) {
                    DatagramChannel dc = (DatagramChannel) key.channel();
                    buf.clear();
                    SocketAddress src = dc.receive(buf);
                    if (src == null) continue;
                    buf.flip();
                    String line = new String(buf.array(), 0, buf.limit()).trim();
                    if (line.isEmpty()) continue;

                    UdpNioContext ctx = new UdpNioContext(dc, src, udpDir);
                    udpReg.dispatch(line, ctx);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        FileUtils.ensureDirectory(PathsConfig.SERVER_TCP);
        FileUtils.ensureDirectory(PathsConfig.SERVER_UDP);

        new NioServer(8888, 9999, PathsConfig.SERVER_TCP, PathsConfig.SERVER_UDP).start();
    }

}