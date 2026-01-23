package network.labs.lab4;

import network.labs.lab1.server.TcpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Рабочий поток: ждёт сокет из очереди и обрабатывает клиента.
 */
public class WorkerThread extends Thread {
    private static final Logger log = LoggerFactory.getLogger(WorkerThread.class);

    private final BlockingQueue<Socket> socketQueue;
    private volatile boolean running = true;
    private volatile boolean idle = true;
    private volatile long lastIdleStart = System.nanoTime();

    public WorkerThread(BlockingQueue<Socket> socketQueue) {
        this.socketQueue = socketQueue;
        setName("Worker-" + getId());
        markIdle();
    }

    @Override
    public void run() {
        try {
            while (running) {
                Socket client = socketQueue.poll(30, TimeUnit.SECONDS);

                if (client != null) {
                    idle = false;
                    log.info("{} обслуживает клиента {}", getName(), client.getRemoteSocketAddress());
                    try {
                       // new TcpServer(0).handleClient(client); // используем логику ЛР1
                        log.info("Клиент {} обслужен", client.getRemoteSocketAddress());
                    } catch (Exception e) {
                        log.error("Ошибка обработки клиента {}", client.getRemoteSocketAddress(), e);
                    } finally {
                        markIdle();
                    }
                } else {
                    // нет работы — остаёмся idle и продолжаем ждать
                    markIdle();
                }
            }
        } catch (InterruptedException e) {
            log.info("{} прерван и завершает работу", getName());
        } finally {
            log.info("{} завершён", getName());
        }
    }

    private void markIdle() {
        idle = true;
        lastIdleStart = System.nanoTime();
    }

    public boolean isIdleLongerThan(int seconds) {
        if (!idle) return false;
        long elapsedNs = System.nanoTime() - lastIdleStart;
        return TimeUnit.NANOSECONDS.toSeconds(elapsedNs) >= seconds;
    }

    public void requestStop() {
        running = false;
        interrupt();
    }
}
