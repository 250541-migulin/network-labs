package network.labs.lab2.server;

import network.labs.lab2.common.Config;

public class MainServerUdp {
    public static void main(String[] args) {
        System.out.println("=== Запуск UDP-сервера ЛР-2 ===");
        System.out.println("Порт: " + Config.PORT);
        try {
            new UdpServer(Config.PORT).start();
        } catch (Exception e) {
            System.err.println("❌ Ошибка: " + e.getMessage()); e.printStackTrace(); System.exit(1);
        }
    }
}