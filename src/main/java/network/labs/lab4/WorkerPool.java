package network.labs.lab4;

import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Менеджер пула потоков: поддерживает минимум Nmin, расширяет до Nmax,
 * сжимает при простое (но не ниже Nmin).
 */
public class WorkerPool {
    private final BlockingQueue<Socket> socketQueue;
    private final int nMin;
    private final int nMax;
    private final List<WorkerThread> workers = new ArrayList<>();

    // Порог ожидания без работы, после которого лишние потоки завершаются (секунды)
    private final int idleSecondsToStop = 30;

    public WorkerPool(BlockingQueue<Socket> socketQueue, int nMin, int nMax) {
        this.socketQueue = socketQueue;
        this.nMin = nMin;
        this.nMax = nMax;
    }

    public void start() {
        for (int i = 0; i < nMin; i++) addWorker();

        Thread manager = new Thread(this::managePool, "PoolManager");
        manager.setDaemon(true);
        manager.start();
    }

    private void managePool() {
        try {
            while (true) {
                Thread.sleep(2000); // проверяем каждые 2 секунды

                int active = workers.size();
                int queueSize = socketQueue.size();

                // Расширяем, если есть накопившиеся сокеты и есть запас
                if (queueSize > 0 && active < nMax) {
                    addWorker();
                }

                // Сжимаем, если очередь пуста и потоков больше минимума
                if (queueSize == 0 && active > nMin) {
                    // завершаем один самый «свободный» поток за итерацию
                    Iterator<WorkerThread> it = workers.iterator();
                    while (it.hasNext()) {
                        WorkerThread w = it.next();
                        if (w.isIdleLongerThan(idleSecondsToStop)) {
                            w.requestStop();
                            it.remove();
                            break;
                        }
                    }
                }
            }
        } catch (InterruptedException ignored) {
        }
    }

    private void addWorker() {
        WorkerThread worker = new WorkerThread(socketQueue);
        workers.add(worker);
        worker.start();
    }
}
