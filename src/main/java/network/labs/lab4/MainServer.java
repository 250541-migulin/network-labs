package network.labs.lab4;

/**
 * Запуск TCP-сервера с пулом потоков (ЛР №4).
 * Аргументы:
 *   [port] [nMin] [nMax]
 * Если аргументы не переданы, используются значения по умолчанию.
 */
public class MainServer {
    public static void main(String[] args) {
        int port;
        int nMin;
        int nMax;

        if (args.length >= 3) {
            port = Integer.parseInt(args[0]);
            nMin = Integer.parseInt(args[1]);
            nMax = Integer.parseInt(args[2]);
        } else if (args.length == 1) {
            port = Integer.parseInt(args[0]);
            nMin = 2;
            nMax = 10;
        } else {
            port = 8888;
            nMin = 2;
            nMax = 10;
        }

        if (nMin < 1) nMin = 1;
        if (nMax < nMin) nMax = nMin;

        System.out.printf("Запуск сервера: port=%d, nMin=%d, nMax=%d%n", port, nMin, nMax);

        new ThreadPoolTcpServer(port, nMin, nMax).start();
    }
}
