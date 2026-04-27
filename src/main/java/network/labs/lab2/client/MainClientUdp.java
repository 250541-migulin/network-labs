package network.labs.lab2.client;

import network.labs.lab2.common.Config;

public class MainClientUdp {
    public static void main(String[] args) {
        System.out.println("=== Запуск UDP-клиента ЛР-2 ===");
        System.out.println("Цель: " + Config.SERVER_HOST + ":" + Config.PORT);
        try {
            new UdpClient(Config.SERVER_HOST, Config.PORT).start();
        } catch (Exception e) {
            System.err.println("❌ Ошибка: " + e.getMessage()); e.printStackTrace(); System.exit(1);
        }
    }
}