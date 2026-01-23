package network.labs.lab4;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * TCP-сервер с пулом потоков (ЛР4, вариант 8).
 * Главный поток делает accept(), рабочим потокам передаём готовые сокеты через очередь.
 */
public class ThreadPoolTcpServer {
    private static final Logger log = LoggerFactory.getLogger(ThreadPoolTcpServer.class);

    private final int port;
    private final int nMin;
    private final int nMax;
    private final BlockingQueue<Socket> socketQueue = new LinkedBlockingQueue<>();

    private final WorkerPool pool;

    public ThreadPoolTcpServer(int port, int nMin, int nMax) {
        this.port = port;
        this.nMin = nMin;
        this.nMax = nMax;
        this.pool = new WorkerPool(socketQueue, nMin, nMax);
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log.info("TCP-сервер (ЛР4) запущен на порту {}", port);

            pool.start(); // запускаем пул потоков

            while (true) {
                Socket client = serverSocket.accept();
                log.info("Подключился клиент: {}", client.getRemoteSocketAddress());

                // Требование ЛР1: держим keepalive включённым
                client.setKeepAlive(true);

                // Для telnet-тестов убираем таймаут чтения, чтобы сессии не рвались самопроизвольно
                // Если нужно, можно включить длительный таймаут (напр. 5 минут)
                // client.setSoTimeout(300_000);

                socketQueue.put(client); // «передача дескриптора» через очередь
            }
        } catch (IOException | InterruptedException e) {
            log.error("Сервер остановлен", e);
            Thread.currentThread().interrupt();
        }
    }
}
