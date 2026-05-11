package network.labs.lab4.server;

import network.labs.lab4.config.Config;

/**
 * Точка входа в TCP-сервер ЛР-4, вариант 8.
 * Отвечает только за создание экземпляра сервера и передачу ему управления.
 * Все настройки (IP, порт, пути) вынесены в Config.
 */
public final class MainServerTcp {

    /** Приватный конструктор запрещает создание экземпляров утилитарного класса. */
    private MainServerTcp() {
        // намеренно оставлен пустым
    }

    /**
     * Основной метод приложения.
     * Инициализирует сервер и блокирует поток выполнения до остановки приложения.
     *
     * @param args аргументы командной строки не используются, всё настраивается в Config
     */
    public static void main(String[] args) {
        System.out.println("=== Запуск TCP-сервера ЛР-4 (Вариант 8) ===");
        System.out.println("Ожидание подключений на " + Config.SERVER_HOST + ":" + Config.SERVER_PORT);

        try {
            // start() запускает инициализацию пула, сокета и бесконечный цикл accept
            new TcpServer().start();
        } catch (Exception e) {
            // Здесь ловятся только фатальные ошибки старта (например, BindException)
            System.err.println("Критическая ошибка запуска сервера: " + e.getMessage());
            System.exit(1);
        }
    }
}