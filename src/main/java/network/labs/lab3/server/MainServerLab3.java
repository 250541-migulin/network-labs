package network.labs.lab3.server;

/**
 * Точка входа мультиплексированного сервера Лабораторной работы №3.
 * Инициализирует и запускает сервер.
 */
public final class MainServerLab3 {
    private MainServerLab3() {}

    public static void main(String[] args) {
        System.out.println("=== Starting Lab 3 Server (Selector/Multiplexing) ===");
        try {
            new MultiplexedServer().start();
        } catch (Exception e) {
            System.err.println("Critical server error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}