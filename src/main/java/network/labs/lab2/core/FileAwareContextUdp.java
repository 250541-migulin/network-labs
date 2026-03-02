package network.labs.lab2.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.file.Path;

public interface FileAwareContextUdp extends CommandContextUdp {
    Path filesDir();
    InputStream inputStream() throws IOException;
    OutputStream outputStream() throws IOException;

    DatagramSocket getSocket();
    InetSocketAddress getClientAddress();
}