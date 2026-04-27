package network.labs.lab1.client;

import network.labs.lab1.common.Config;
import network.labs.lab1.common.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

/**
 * TCP-клиент для ЛР-01.
 */
public class TcpClient {
    private static final Logger log = LoggerFactory.getLogger(TcpClient.class);

    /**
     * Основной цикл клиента: подключается к серверу, читает команды пользователя,
     * отправляет их на сервер и обрабатывает ответы.
     *
     * Протокол:
     * - Простые команды (ECHO, TIME, CLOSE): отправил → получил ответ → показал пользователю
     * - Сложные команды (UPLOAD, DOWNLOAD): отправил команду → делегирует обработку в handleUpload/Download
     * - Неизвестные команды: сервер вернёт ошибку, клиент её покажет
     *
     * Завершение: по команде CLOSE, по разрыву соединения или по исключению.
     */
    /**
     * Основной цикл клиента: подключается к серверу, читает команды пользователя,
     * отправляет их на сервер и обрабатывает ответы.
     */
    public void start() throws IOException {
        log.debug("start: вход в метод");

        try (Socket socket = new Socket(Config.TCP_SERVER_HOST, Config.TCP_SERVER_PORT)) {

            log.debug("start: сокет создан, подключаюсь к {}:{}",
                    Config.TCP_SERVER_HOST, Config.TCP_SERVER_PORT);
            log.info("Подключено к {}:{}", Config.TCP_SERVER_HOST, Config.TCP_SERVER_PORT);

            try (InputStream in = socket.getInputStream();
                 OutputStream out = socket.getOutputStream()) {

                log.debug("start: потоки получены, запускаю цикл ввода");
                Scanner scanner = new Scanner(System.in, "UTF-8");

                while (true) {
                    System.out.print("> ");
                    String line = scanner.nextLine().trim();

                    if (line.isEmpty()) {
                        log.debug("start: пустая строка, пропускаю");
                        continue;
                    }

                    log.debug("start: пользователь ввёл '{}'", line);

                    String cmd = line.split("\\s+")[0].toUpperCase();
                    log.debug("start: распознана команда '{}'", cmd);

                    switch (cmd) {
                        case "ECHO", "TIME" -> {
                            log.debug("start: обработка простой команды '{}'", cmd);
                            // Простые команды: отправляем и сразу читаем ответ
                            IoUtils.writeLine(out, line);
                            String response = IoUtils.readLine(in);
                            if (response == null) {
                                log.debug("start: ответ null, соединение закрыто");
                                System.out.println("\nСервер закрыл соединение");
                                return;
                            }
                            log.debug("start: получен ответ '{}'", response);
                            System.out.println("Сервер: " + response);
                        }

                        case "CLOSE" -> {
                            log.debug("start: обработка команды CLOSE");
                            IoUtils.writeLine(out, line);
                            String response = IoUtils.readLine(in);
                            log.debug("start: ответ на CLOSE: '{}'", response);
                            System.out.println("Сервер: " + response);
                            return;
                        }

                        case "UPLOAD" -> {
                            log.debug("start: делегирую обработку UPLOAD (команда отправляется внутри handleUpload)");
                            // Сложные команды: НЕ отправляем здесь, отправку делает handleUpload
                            handleUpload(line, in, out);
                        }

                        case "DOWNLOAD" -> {
                            log.debug("start: делегирую обработку DOWNLOAD (команда отправляется внутри handleDownload)");
                            // Сложные команды: НЕ отправляем здесь, отправку делает handleDownload
                            handleDownload(line, in, out);
                        }

                        default -> {
                            log.debug("start: неизвестная команда '{}', отправляю и жду ответ", cmd);
                            IoUtils.writeLine(out, line);
                            String response = IoUtils.readLine(in);
                            if (response == null) {
                                log.debug("start: ответ null, соединение закрыто");
                                System.out.println("\nСервер закрыл соединение");
                                return;
                            }
                            log.debug("start: ответ на неизвестную команду: '{}'", response);
                            System.out.println("Сервер: " + response);
                        }
                    }
                }

            }
        }
    }

    /**
     * Обработка команды UPLOAD: клиент отправляет файл на сервер.
     *
     * Протокол обмена:
     * 1. Клиент проверяет файл локально (если нет — ошибка, ничего не отправляем)
     * 2. Клиент → Сервер: "UPLOAD <filename>"
     * 3. Сервер → Клиент: "OK <offset>" (offset = сколько байт уже есть на сервере)
     * 4. Клиент → Сервер: <remaining> (сколько байт осталось отправить)
     * 5. Клиент → Сервер: [файловые данные, начиная с offset]
     * 6. Сервер → Клиент: "Файл загружен: <filename>"
     *
     * Поддержка докачки: если файл частично загружен и клиент тот же —
     * продолжаем с места обрыва, не отправляем уже загруженные байты.
     *
     * @param line команда от пользователя, например "upload file.bin"
     * @param in InputStream из сокета (чтение ответов сервера)
     * @param out OutputStream в сокет (отправка данных серверу)
     */
    private void handleUpload(String line, InputStream in, OutputStream out) throws IOException {
        // 0. Вход в метод
        log.debug("handleUpload: старт, команда='{}'", line);

        String[] parts = line.split("\\s+");
        if (parts.length < 2) {
            System.out.println("Использование: UPLOAD <имя файла>");
            log.debug("handleUpload: ошибка — не указано имя файла, возврат");
            return;
        }

        String filename = parts[1];
        Path file = Config.SOURCE_DIR.resolve(filename);

        // 1. Проверяем файл локально ПЕРЕД любыми сетевыми операциями
        log.debug("handleUpload: шаг 1 — проверка файла '{}', существует={}", file, Files.exists(file));
        if (!Files.exists(file)) {
            System.out.println("Файл не найден: " + file);
            log.debug("handleUpload: файл не найден, возврат без отправки команды");
            return;
        }

        // 2. Отправляем команду серверу
        log.debug("handleUpload: шаг 2 — отправка команды 'UPLOAD {}'", filename);
        IoUtils.writeLine(out, "UPLOAD " + filename);

        // 3. Ждём ответ от сервера: "OK <offset>" или ошибка
        log.debug("handleUpload: шаг 3 — чтение ответа сервера...");
        String response = IoUtils.readLine(in);
        log.debug("handleUpload: шаг 3 — ответ получен: '{}'", response);

        if (response == null) {
            System.out.println("Сервер закрыл соединение");
            log.debug("handleUpload: ответ null, соединение закрыто, возврат");
            return;
        }
        if (!response.startsWith("OK")) {
            System.out.println("Сервер: " + response);
            log.debug("handleUpload: ответ не начинается с 'OK', возврат");
            return;
        }

        // 4. Парсим offset для докачки
        long offset = 0;
        if (response.length() > 3) {
            offset = Long.parseLong(response.substring(3).trim());
        }
        log.debug("handleUpload: шаг 4 — offset={}", offset);

        long fileSize = Files.size(file);
        long remaining = fileSize - offset;
        log.debug("handleUpload: шаг 4 — размер файла={}, осталось отправить={}", fileSize, remaining);

        // 5. Если файл уже полностью загружен — завершаем
        if (remaining == 0) {
            System.out.println("Файл уже полностью загружен на сервере");
            IoUtils.readLine(in); // Прочитать финальное сообщение сервера
            log.debug("handleUpload: шаг 5 — remaining=0, файл загружен полностью, возврат");
            return;
        }

        // 6. Отправляем размер остатка (сервер ждёт это число перед приёмом данных!)
        log.debug("handleUpload: шаг 6 — отправка размера остатка: {}", remaining);
        IoUtils.writeLine(out, String.valueOf(remaining));

        log.info("Отправка: {} ({} байт, докачка с {})", filename, remaining, offset);

        // 7. Отправляем файл: файл -> сеть
        log.debug("handleUpload: шаг 7 — вызов copyFileToStream (файл -> сеть)");
        long sent = IoUtils.copyFileToStream(file, out, offset);
        log.debug("handleUpload: шаг 7 — copyFileToStream завершён, отправлено {} байт", sent);
        log.info("Отправлено {} байт", sent);

        // 8. Ждём финальное подтверждение от сервера
        log.debug("handleUpload: шаг 8 — чтение финального подтверждения...");
        response = IoUtils.readLine(in);
        log.debug("handleUpload: шаг 8 — финальный ответ: '{}'", response);
        System.out.println("Сервер: " + response);

        log.debug("handleUpload: завершение");
    }

    /**
     * Обработка команды DOWNLOAD: клиент получает файл с сервера.
     *
     * Протокол обмена:
     * 1. Клиент проверяет локальный файл (если есть — запоминаем размер для докачки)
     * 2. Клиент → Сервер: <offset> (сколько байт уже есть локально)
     * 3. Сервер → Клиент: "OK"
     * 4. Сервер → Клиент: <remaining> (сколько байт будет отправлено)
     * 5. Если remaining > 0: Сервер → Клиент: [файловые данные, начиная с offset]
     * 6. Сервер → Клиент: "Файл отправлен: <filename>"
     *
     * Поддержка докачки: если файл частично скачан — клиент сообщает серверу,
     * с какого места продолжить, и данные дописываются в существующий файл.
     *
     * @param line команда от пользователя, например "download file.bin"
     * @param in InputStream из сокета (чтение данных от сервера)
     * @param out OutputStream в сокет (отправка offset и других данных серверу)
     */
    private void handleDownload(String line, InputStream in, OutputStream out) throws IOException {
        log.debug("handleDownload: старт, команда='{}'", line);

        String[] parts = line.split("\\s+");
        if (parts.length < 2) {
            System.out.println("Использование: DOWNLOAD <имя файла>");
            log.debug("handleDownload: ошибка — не указано имя файла, возврат");
            return;
        }

        String filename = parts[1];
        Path target = Config.TMP_DIR.resolve("downloaded_" + filename);

        // 1. Проверяем локальный файл для докачки
        long localSize = Files.exists(target) ? Files.size(target) : 0;
        log.debug("handleDownload: шаг 1 — локальный файл '{}', размер={}", target, localSize);

        if (localSize > 0) {
            log.info("Найден локальный файл ({} байт). Проверяем докачку...", localSize);
            log.debug("handleDownload: файл найден, возможна докачка с байта {}", localSize);
        }

        // 2. Отправляем команду серверу (ПЕРВАЯ отправка в протоколе!)
        log.debug("handleDownload: шаг 2 — отправка команды 'DOWNLOAD {}'", filename);
        IoUtils.writeLine(out, "DOWNLOAD " + filename);

        // 3. Ждём статус от сервера (ОК или ошибка)
        log.debug("handleDownload: шаг 3 — чтение статуса от сервера...");
        String status = IoUtils.readLine(in);
        log.debug("handleDownload: шаг 3 — статус получен: '{}'", status);

        if (status == null) {
            System.out.println("Сервер закрыл соединение");
            log.debug("handleDownload: статус null, соединение закрыто, возврат");
            return;
        }
        if (!"OK".equals(status)) {
            System.out.println("Сервер: " + status);
            log.debug("handleDownload: статус не 'OK', возврат");
            return;
        }

        // 4. Только после "OK" отправляем offset!
        log.debug("handleDownload: шаг 4 — отправка offset={} серверу", localSize);
        IoUtils.writeLine(out, String.valueOf(localSize));

        // 5. Читаем размер остатка
        log.debug("handleDownload: шаг 5 — чтение размера остатка...");
        String sizeLine = IoUtils.readLine(in);
        log.debug("handleDownload: шаг 5 — размер остатка получен: '{}'", sizeLine);

        if (sizeLine == null) {
            System.out.println("Сервер закрыл соединение");
            log.debug("handleDownload: sizeLine null, соединение закрыто, возврат");
            return;
        }
        long remaining = Long.parseLong(sizeLine.trim());
        long totalFile = localSize + remaining;
        log.debug("handleDownload: шаг 5 — remaining={}, общий размер файла={}", remaining, totalFile);

        // 6. Если файл уже актуален — завершаем
        if (remaining == 0) {
            System.out.println("Файл уже актуален");
            IoUtils.readLine(in);
            log.debug("handleDownload: шаг 6 — remaining=0, файл актуален, возврат");
            return;
        }

        log.info("Приём: {} ({} байт, всего: {} байт)", filename, remaining, totalFile);
        log.debug("handleDownload: шаг 6 — начинаю приём данных, ожидаю {} байт", remaining);

        // 7. Принимаем данные: сеть -> файл (с лимитом!)
        log.debug("handleDownload: шаг 7 — вызов copyStreamToFile (сеть -> файл, лимит={})", remaining);
        long received = IoUtils.copyStreamToFile(in, target, localSize > 0, remaining);
        log.debug("handleDownload: шаг 7 — copyStreamToFile завершён, получено {} байт", received);
        log.info("Получено {} байт", received);

        // 8. Ждём финальное подтверждение от сервера
        log.debug("handleDownload: шаг 8 — чтение финального подтверждения...");
        String response = IoUtils.readLine(in);
        log.debug("handleDownload: шаг 8 — финальный ответ: '{}'", response);

        System.out.println("Сервер: " + response);
        System.out.println("Файл сохранён: " + target.getFileName());

        log.debug("handleDownload: завершение, файл сохранён в '{}'", target);
    }
}