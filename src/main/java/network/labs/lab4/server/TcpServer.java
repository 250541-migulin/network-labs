package network.labs.lab4.server;

import network.labs.lab4.config.Config;
import network.labs.lab4.config.PoolConfig;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Главный процесс TCP-сервера (ЛР-4, вариант 8).
 * Отвечает только за прослушивание порта, приём подключений (accept)
 * и передачу дескриптора сокета в пул потоков для дальнейшей обработки.
 */
public final class TcpServer {

    private ServerSocket serverSocket;
    private ExecutorService threadPool;

    /**
     * Точка входа в работу сервера. Инициализирует ресурсы и запускает цикл приёма.
     */
    public void start() throws IOException {
        initializePool();
        setupServerSocket();
        registerShutdownHook();
        runAcceptLoop();
    }

    /**
     * Создаёт пул потоков с динамическим расширением по конфигурации.
     */
    private void initializePool() {
        threadPool = new ThreadPoolExecutor(
                PoolConfig.CORE_POOL_SIZE,
                PoolConfig.MAX_POOL_SIZE,
                PoolConfig.KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(PoolConfig.QUEUE_CAPACITY)
        );
        System.out.println("Пул потоков создан: min=" + PoolConfig.CORE_POOL_SIZE +
                ", max=" + PoolConfig.MAX_POOL_SIZE);
    }

    /**
     * Открывает серверный сокет для прослушивания входящих подключений.
     */
    private void setupServerSocket() throws IOException {
        serverSocket = new ServerSocket(Config.SERVER_PORT);
        System.out.println("Сервер запущен на порту " + Config.SERVER_PORT);
    }

    /**
     * Регистрирует обработчик для корректного завершения при SIGINT/Ctrl+C.
     */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    /**
     * Основной цикл ожидания подключений.
     * При получении клиента создаёт обработчик и отдаёт его в пул.
     */
    private void runAcceptLoop() {
        try {
            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Новое подключение: " + clientSocket.getRemoteSocketAddress());
                // Передача дескриптора соединения рабочему потоку пула
                threadPool.submit(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            // Ожидаемое исключение при вызове close() во время shutdown
            if (!serverSocket.isClosed()) {
                System.err.println("Ошибка в цикле accept: " + e.getMessage());
            }
        }
    }

    /**
     * Последовательное освобождение ресурсов при завершении работы.
     */
    private void shutdown() {
        System.out.println("\nИнициализация завершения работы сервера...");
        closeServerSocket();
        stopThreadPool();
    }

    /**
     * Закрывает слушающий сокет, чтобы разблокировать метод accept().
     */
    private void closeServerSocket() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                System.out.println("Серверный сокет закрыт.");
            }
        } catch (IOException e) {
            System.err.println("Ошибка закрытия сокета: " + e.getMessage());
        }
    }

    /**
     * Останавливает пул потоков: завершает текущие задачи, затем принудительно, если нужно.
     */
    private void stopThreadPool() {
        threadPool.shutdown();
        try {
            boolean terminated = threadPool.awaitTermination(10, TimeUnit.SECONDS);
            if (!terminated) {
                System.out.println("Пул не завершился за 10 сек. Принудительная остановка...");
                threadPool.shutdownNow();
            } else {
                System.out.println("Пул потоков успешно завершил работу.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Прерывание ожидания завершения пула.");
        }
    }
}