package network.labs.lab3.client;

import network.labs.lab1.common.FileUtils;
import network.labs.lab2.util.PathsConfig;
import network.labs.lab3.client.commands.*;

import java.io.IOException;
import java.util.Scanner;

public class TcpNioClient {
    public static void main(String[] args) throws IOException {
        FileUtils.ensureDirectory(PathsConfig.CLIENT_TCP);
        ClientContext ctx = ClientContext.connect("localhost", 8888);

        ClientCommandRegistry reg = new ClientCommandRegistry();
        reg.register(new PingCommandClient());
        reg.register(new TimeCommandClient());
        reg.register(new EchoCommandClient());
        reg.register(new CloseCommandClient());
        reg.register(new UploadCommandClient());
        reg.register(new DownloadCommandClient());


        Scanner sc = new Scanner(System.in);
        System.out.println("🔗 TCP клиент подключен. Введите команду (PING, TIME, ECHO <msg>, UPLOAD <file>, DOWNLOAD <file>, CLOSE):");

        while (sc.hasNextLine()) {
            String line = sc.nextLine();
            reg.dispatch(line, ctx);
        }
    }
}
