package network.labs.lab2.client;

import network.labs.lab2.common.Config;

/**
 * Точка входа UDP-клиента для лабораторной работы №2.
 * Инициализирует клиентское соединение с сервером и запускает интерактивный цикл команд.
 */
public class MainClientUdp {

    /**
     * Основной метод приложения.
     * Создаёт экземпляр UDP-клиента с параметрами из Config и запускает его работу.
     * В случае нештатной ситуации выводит диагностическую информацию и завершает процесс.
     *
     * @param args аргументы командной строки (не используются)
     */
    public static void main(String[] args) {
        System.out.println("=== Запуск UDP-клиента ЛР-2 ===");
        System.out.println("Цель: " + Config.SERVER_HOST + ":" + Config.PORT);

        try {
            // Инициализация и запуск клиентской сессии
            new UdpClient(Config.SERVER_HOST, Config.PORT).start();
        } catch (Exception e) {
            // Логирование ошибки и корректное завершение работы приложения
            System.err.println("Ошибка запуска клиента: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}