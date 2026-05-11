package network.labs.lab1.client;

import network.labs.lab1.common.Config;

/**
 * Точка входа TCP-клиента для Лабораторной работы №1.
 * Инициализирует клиентский компонент и запускает интерактивный сеанс связи с сервером.
 */
public final class MainClientTcp {

    // Приватный конструктор предотвращает создание экземпляров
    private MainClientTcp() {}

    /**
     * Основной метод приложения.
     * Выводит информацию о запуске, создаёт экземпляр клиента и передаёт управление.
     * При ошибке подключения или работы сети выводит сообщение и завершает работу.
     *
     * @param args аргументы командной строки (не используются, настройки в Config)
     */
    public static void main(String[] args) {

        String host = args.length > 0 ? args[0] : Config.SERVER_HOST;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : Config.SERVER_PORT;

        System.out.println("=== Запуск TCP-клиента ЛР-1 ===");
        System.out.println("Цель: " + host + ":" + port);

        try {
            // start() запускает интерактивный цикл, блокирующий поток до завершения сессии
            new TcpClient().start(host, port);
        } catch (Exception e) {
            System.err.println("Ошибка запуска клиента: " + e.getMessage());
            // Для отладки можно раскомментировать строку ниже:
            // e.printStackTrace();
            System.exit(1);
        }
    }
}