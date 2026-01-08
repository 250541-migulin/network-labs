package network.labs.lab3.client;

import network.labs.lab1.common.FileUtils;
import network.labs.lab2.util.PathsConfig;
import network.labs.lab3.client.commands.*;

import java.io.IOException;
import java.util.Scanner;

public class UdpNioClient {
    public static void main(String[] args) throws IOException {
        FileUtils.ensureDirectory(PathsConfig.CLIENT_UDP);
        UdpClientContext ctx = UdpClientContext.connect("localhost", 9999);

        UdpClientCommandRegistry reg = new UdpClientCommandRegistry();
        reg.register(new PingCommandUdpClient());
        reg.register(new TimeCommandUdpClient());
        reg.register(new EchoCommandUdpClient());
        reg.register(new CloseCommandUdpClient());
        reg.register(new DownloadCommandUdp());
        reg.register(new UploadCommandUdp());


        Scanner sc = new Scanner(System.in);
        System.out.println("🔗 UDP клиент подключен. Введите команду (PING, TIME, ECHO <msg>, UPLOAD <file>, DOWNLOAD <file>, CLOSE):");

        while (sc.hasNextLine()) {
            String line = sc.nextLine();
            reg.dispatch(line, ctx);
        }
    }
}
