package network.labs.lab1.server;

import network.labs.lab1.common.Config;
import network.labs.lab1.common.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Последовательный TCP-сервер (ЛР №1).
 * Поддерживает докачку файлов.
 *
 * Архитектура:
 * - Сервер обрабатывает одного клиента за раз (последовательная модель)
 * - backlog=1: одно подключение обрабатывается, одно может ждать в очереди ОС
 * - Состояние докачки (lastClientIp, lastFilename) хранится в памяти сервера
 *   и сбрасывается при перезапуске сервера
 *
 * Цикл работы:
 * 1. Создать ServerSocket на порту из конфига
 * 2. Бесконечный цикл: accept() → handleClient() → repeat
 * 3. При ошибке: залогировать и завершить работу сервера
 */
public class TcpServer {
    private static final Logger log = LoggerFactory.getLogger(TcpServer.class);

    // Состояние для докачки: запоминаем последнего клиента и файл
    // Нужно, чтобы при повторном подключении того же клиента продолжить с места обрыва
    private InetAddress lastClientIp = null;
    private String lastFilename = null;

    /**
     * Запускает сервер: создаёт сокет, входит в цикл приёма подключений.
     * Метод блокирующий — не вернёт управление, пока сервер работает.
     */
    public void start() {

        try (ServerSocket serverSocket = new ServerSocket(Config.TCP_SERVER_PORT, 1)) {

            log.info("Сервер запущен на порту {}", Config.TCP_SERVER_PORT);

            while (true) {
                log.debug("start: ожидание нового подключения (accept)...");
                acceptAndHandleClient(serverSocket);
                log.debug("start: обработка клиента завершена, жду следующего");
            }

        } catch (IOException e) {
            log.error("Сервер остановлен из-за ошибки: {}", e.getMessage(), e);
        }
    }

    /**
     * Принимает подключение клиента и передаёт управление в handleClient().
     *
     * Этот метод — точка входа для каждого нового клиента:
     * 1. Блокируется на serverSocket.accept() до появления подключения
     * 2. Настраивает сокет: Keep-Alive для детекта обрывов, таймаут на чтение
     * 3. Делегирует обработку в handleClient() (блокирует поток, пока клиент активен)
     * 4. Логирует завершение сессии
     *
     * Обработка ошибок:
     * - SocketException: клиент разорвал соединение (нормальная ситуация)
     * - IOException: другие проблемы с сетью (логируем как ошибку)
     *
     * @param serverSocket сокет сервера, на котором слушаем порт
     */
    private void acceptAndHandleClient(ServerSocket serverSocket) {
        log.debug("acceptAndHandleClient: ожидание подключения через accept()...");

        try {
            // 1. Принимаем подключение (блокируется, пока не придёт клиент)
            Socket client = serverSocket.accept();
            InetAddress clientIp = client.getInetAddress();

            log.info("Подключился клиент: {} (IP: {})",
                    client.getRemoteSocketAddress(), clientIp);

            // 2. Настраиваем сокет для надёжной работы
            client.setKeepAlive(true);
            client.setSoTimeout(Config.SOCKET_TIMEOUT_MS);

            // 3. Делегируем обработку клиента (блокирует поток, пока клиент активен)
            log.debug("acceptAndHandleClient: вызов handleClient() для обработки команд");
            handleClient(client, clientIp);

            // 4. Клиент завершил сессию
            log.debug("acceptAndHandleClient: handleClient() завершён, клиент отключился");
            log.info("Клиент отключился");

        } catch (SocketException e) {
            // Нормальная ситуация: клиент закрыл соединение (FIN/RST)
            log.debug("acceptAndHandleClient: клиент разорвал соединение: {}", e.getMessage());
            log.info("Клиент разорвал соединение: {}", e.getMessage());

        } catch (IOException e) {
            // Неожиданная ошибка: проблемы с сетью, сокетом и т.д.
            log.debug("acceptAndHandleClient: ошибка при работе с клиентом", e);
            log.error("Ошибка при работе с клиентом: {}", e.getMessage(), e);
        }

        log.debug("acceptAndHandleClient: метод завершён, возврат в цикл accept()");
    }

    /**
     * Обрабатывает сессию одного клиента: читает команды из потока,
     * распознаёт их и делегирует выполнение соответствующим обработчикам.
     *
     * Протокол работы:
     * - Цикл: читаем команду → распознаём → выполняем → повторяем
     * - Команда — первое слово строки, регистр не важен (приводим к верхнему)
     * - Простые команды (ECHO, TIME, CLOSE): обрабатываются сразу
     * - Сложные команды (UPLOAD, DOWNLOAD): делегируются в handleUpload/Download
     * - Неизвестные команды: сервер возвращает "Error: unknown command"
     *
     * Завершение сессии:
     * - Команда CLOSE: сервер отправляет "OK" и закрывает соединение
     * - Таймаут 2 минуты: сервер закрывает соединение по SocketTimeoutException
     * - Разрыв клиентом: сервер логирует и корректно завершает
     *
     * @param client сокет клиента (закрывается автоматически в try-with-resources)
     * @param clientIp IP-адрес клиента (нужен для логики докачки)
     */
    private void handleClient(Socket client, InetAddress clientIp) {
        log.debug("handleClient: старт, клиент={}", client.getRemoteSocketAddress());

        try (client;
             InputStream in = client.getInputStream();
             OutputStream out = client.getOutputStream()) {

            log.debug("handleClient: потоки получены, вхожу в цикл чтения команд");

            String line;
            // Цикл: читаем команды, пока клиент не отключится или не отправит CLOSE
            while ((line = IoUtils.readLine(in)) != null) {

                log.debug("handleClient: получена команда от клиента: '{}'", line);

                // Распознаём команду: первое слово, приводим к верхнему регистру
                String cmd = line.split("\\s+")[0].toUpperCase();
                log.debug("handleClient: распознана команда: '{}'", cmd);

                switch (cmd) {
                    case "ECHO" -> {
                        log.debug("handleClient: делегирую обработку ECHO");
                        handleEcho(line, out);
                    }
                    case "TIME" -> {
                        log.debug("handleClient: делегирую обработку TIME");
                        handleTime(out);
                    }
                    case "CLOSE" -> {
                        log.debug("handleClient: обработка CLOSE — завершение сессии");
                        handleClose(out);
                        log.debug("handleClient: возврат из метода по команде CLOSE");
                        return;
                    }
                    case "UPLOAD" -> {
                        log.debug("handleClient: делегирую обработку UPLOAD, IP={}", clientIp);
                        handleUpload(line, in, out, clientIp);
                    }
                    case "DOWNLOAD" -> {
                        log.debug("handleClient: делегирую обработку DOWNLOAD, IP={}", clientIp);
                        handleDownload(line, in, out, clientIp);
                    }
                    default -> {
                        log.debug("handleClient: неизвестная команда '{}', отправляю ошибку", cmd);
                        IoUtils.writeLine(out, "Error: unknown command");
                    }
                }

                log.debug("handleClient: команда обработана, жду следующую");
            }

            log.debug("handleClient: возврат из метода (нормальное завершение)");

        } catch (SocketTimeoutException e) {
            // Клиент не отправлял данные 2 минуты — сервер закрывает соединение
            log.info("Клиент {} неактивен более 2 минут — соединение закрыто сервером", clientIp);

        } catch (SocketException e) {
            // Клиент резко закрыл соединение (FIN/RST)
            log.info("Клиент разорвал соединение: {}", e.getMessage());

        } catch (IOException e) {
            // Неожиданная ошибка сети или ввода-вывода
            log.error("Ошибка обработки клиента {}: {}", clientIp, e.getMessage(), e);
        }
    }

    /**
     * ECHO: возвращает клиенту текст после команды.
     */
    private void handleEcho(String line, OutputStream out) throws IOException {
        String msg = line.length() > 5 ? line.substring(5).trim() : "";
        IoUtils.writeLine(out, msg);
    }

    /**
     * TIME: отправляет текущее время сервера.
     */
    private void handleTime(OutputStream out) throws IOException {
        IoUtils.writeLine(out, "Время: " + LocalDateTime.now());
    }

    /**
     * CLOSE: подтверждает закрытие соединения.
     */
    private void handleClose(OutputStream out) throws IOException {
        IoUtils.writeLine(out, "Соединение закрыто");
    }

    /**
     * Обработка команды UPLOAD: сервер принимает файл от клиента.
     *
     * Протокол обмена:
     * 1. Сервер получает команду "UPLOAD <filename>" (уже прочитана в handleClient)
     * 2. Сервер проверяет докачку: если тот же клиент и файл — находит смещение
     * 3. Сервер → Клиент: "OK <offset>" (сколько байт уже есть на сервере)
     * 4. Клиент → Сервер: <remaining> (сколько байт осталось отправить)
     * 5. Если remaining > 0: Клиент → Сервер: [файловые данные, начиная с offset]
     * 6. Сервер → Клиент: "Файл загружен: <filename>"
     *
     * Поддержка докачки:
     * - Состояние (последний клиент + файл) хранится в полях lastClientIp/lastFilename
     * - Если клиент переподключился и файл частично есть — продолжаем с места обрыва
     * - Если файл есть, но клиент другой — удаляем старый файл, начинаем заново
     * - Состояние сбрасывается при перезапуске сервера (хранится в памяти)
     *
     * @param line команда от клиента, например "UPLOAD file.bin" (уже прочитана)
     * @param in InputStream из сокета (чтение данных файла от клиента)
     * @param out OutputStream в сокет (отправка ответов клиенту)
     * @param ip IP-адрес клиента (нужен для проверки докачки)
     */
    private void handleUpload(String line, InputStream in, OutputStream out, InetAddress ip) throws IOException {
        // 0. Вход в метод
        log.debug("handleUpload: старт, команда='{}', клиент={}", line, ip);

        String[] parts = line.split("\\s+");
        if (parts.length < 2) {
            log.debug("handleUpload: ошибка — не указано имя файла, отправляю ошибку клиенту");
            IoUtils.writeLine(out, "ОШИБКА: имя файла не указано");
            return;
        }

        String filename = parts[1];
        Path target = Config.TMP_DIR.resolve(filename);

        // 1. Проверяем докачку: если тот же клиент и файл — продолжаем с места обрыва
        log.debug("handleUpload: шаг 1 — проверка докачки: файл='{}', существует={}", filename, Files.exists(target));

        long offset = 0;
        if (ip.equals(lastClientIp) && filename.equals(lastFilename) && Files.exists(target)) {
            offset = Files.size(target);
            log.info("Докачка: {} байт уже есть", offset);
            log.debug("handleUpload: шаг 1 — докачка: тот же клиент и файл, offset={}", offset);
        } else if (Files.exists(target)) {
            log.debug("handleUpload: шаг 1 — файл существует, но клиент другой, удаляю старый");
            Files.delete(target);
        } else {
            log.debug("handleUpload: шаг 1 — файл не существует, начинаем с нуля");
        }

        // 2. Отправляем OK с offset (клиент ждёт это перед отправкой размера!)
        log.debug("handleUpload: шаг 2 — отправка 'OK {}' клиенту", offset);
        IoUtils.writeLine(out, "OK " + offset);

        // 3. Читаем размер остатка от клиента (КРИТИЧНЫЙ ШАГ: сервер ждёт число!)
        log.debug("handleUpload: шаг 3 — чтение размера остатка от клиента...");
        String sizeLine = IoUtils.readLine(in);
        log.debug("handleUpload: шаг 3 — размер получен: '{}'", sizeLine);

        if (sizeLine == null) {
            log.debug("handleUpload: шаг 3 — sizeLine=null, клиент отключился, возврат");
            return;
        }

        // 4. Парсим remaining (если здесь ошибка — клиент отправил не число!)
        log.debug("handleUpload: шаг 4 — парсинг remaining из строки='{}'", sizeLine);
        long remaining = Long.parseLong(sizeLine.trim());
        log.debug("handleUpload: шаг 4 — remaining={}", remaining);

        // 5. Проверка на полный файл (всё уже загружено ранее)
        if (remaining == 0) {
            log.debug("handleUpload: шаг 5 — remaining=0, файл уже загружен полностью");
            IoUtils.writeLine(out, "Файл уже загружен");
            log.debug("handleUpload: шаг 5 — отправлено подтверждение, возврат");
            return;
        }

        log.info("Приём файла: {} ({} байт)", filename, remaining);
        log.debug("handleUpload: шаг 5 — начинаю приём данных, ожидаю {} байт", remaining);

        // 6. Приём данных: сеть -> файл (с лимитом! читаем ровно remaining байт)
        log.debug("handleUpload: шаг 6 — вызов copyStreamToFile (сеть -> файл, лимит={})", remaining);
        long received = IoUtils.copyStreamToFile(in, target, offset > 0, remaining);
        log.debug("handleUpload: шаг 6 — copyStreamToFile завершён, получено {} байт", received);
        log.info("Получено {} байт", received);

        // 7. Финальное подтверждение + сохранение состояния для докачки
        log.debug("handleUpload: шаг 7 — отправка финального подтверждения");
        lastClientIp = ip;
        lastFilename = filename;
        log.debug("handleUpload: шаг 7 — сохранено состояние для докачки: IP={}, файл={}", ip, filename);

        IoUtils.writeLine(out, "Файл загружен: " + filename);

        log.debug("handleUpload: завершение");
    }

    /**
     * Обработка команды DOWNLOAD: сервер отправляет файл клиенту.
     *
     * Протокол обмена:
     * 1. Сервер получает команду "DOWNLOAD <filename>" (уже прочитана в handleClient)
     * 2. Сервер проверяет существование файла в источнике
     * 3. Клиент → Сервер: <offset> (сколько байт уже есть у клиента для докачки)
     * 4. Сервер → Клиент: "OK"
     * 5. Сервер → Клиент: <remaining> (сколько байт будет отправлено)
     * 6. Если remaining > 0: Сервер → Клиент: [файловые данные, начиная с offset]
     * 7. Сервер → Клиент: "Файл отправлен: <filename>"
     *
     * Поддержка докачки:
     * - Клиент сообщает смещение (offset), с которого нужно продолжить
     * - Сервер пропускает уже отправленные байты через skipNBytes()
     * - Данные дописываются в файл клиента, а не перезаписываются
     *
     * @param line команда от клиента, например "DOWNLOAD file.bin" (уже прочитана)
     * @param in InputStream из сокета (чтение offset от клиента)
     * @param out OutputStream в сокет (отправка файла и ответов клиенту)
     * @param ip IP-адрес клиента (не используется в DOWNLOAD, но нужен для сигнатуры)
     */
    private void handleDownload(String line, InputStream in, OutputStream out, InetAddress ip) throws IOException {
        // 0. Вход в метод
        log.debug("handleDownload: старт, команда='{}', клиент={}", line, ip);

        String[] parts = line.split("\\s+");
        if (parts.length < 2) {
            log.debug("handleDownload: ошибка — не указано имя файла");
            IoUtils.writeLine(out, "ОШИБКА: имя файла не указано");
            return;
        }

        String filename = parts[1];
        Path source = Config.SOURCE_DIR.resolve(filename);

        // 1. Проверяем, что файл существует на сервере
        log.debug("handleDownload: шаг 1 — проверка файла: путь='{}', существует={}", source, Files.exists(source));

        if (!Files.exists(source)) {
            log.debug("handleDownload: шаг 1 — файл не найден, отправляю ошибку");
            IoUtils.writeLine(out, "ОШИБКА: файл не найден");
            return;
        }
        log.debug("handleDownload: шаг 1 — файл найден, размер={}", Files.size(source));

        // 2. ✅ СНАЧАЛА отправляем "OK" (клиент ждёт это перед отправкой offset!)
        log.debug("handleDownload: шаг 2 — отправка 'OK' клиенту");
        IoUtils.writeLine(out, "OK");

        // 3. Теперь читаем offset от клиента
        log.debug("handleDownload: шаг 3 — чтение offset от клиента...");
        String offsetLine = IoUtils.readLine(in);
        log.debug("handleDownload: шаг 3 — offset получен: '{}'", offsetLine);

        long offset = 0;
        if (offsetLine != null && !offsetLine.trim().isEmpty()) {
            offset = Long.parseLong(offsetLine.trim());
            log.debug("handleDownload: шаг 3 — распаршен offset={}", offset);
        } else {
            log.debug("handleDownload: шаг 3 — offset не получен, использую 0");
        }

        long fileSize = Files.size(source);
        long remaining = fileSize - offset;
        log.debug("handleDownload: шаг 3 — размер файла={}, offset={}, осталось отправить={}",
                fileSize, offset, remaining);

        // 4. Отправляем размер остатка
        log.debug("handleDownload: шаг 4 — отправка размера остатка: {}", remaining);
        IoUtils.writeLine(out, String.valueOf(remaining));

        // 5. Если есть данные для отправки — отправляем файл
        if (remaining > 0) {
            log.info("Отправка файла: {} ({} байт)", filename, remaining);
            log.debug("handleDownload: шаг 5 — начинаю отправку данных, пропуск первых {} байт", offset);

            log.debug("handleDownload: шаг 5 — вызов copyFileToStream (файл -> сеть, skip={})", offset);
            long sent = IoUtils.copyFileToStream(source, out, offset);
            log.debug("handleDownload: шаг 5 — copyFileToStream завершён, отправлено {} байт", sent);
            log.info("Отправлено {} байт", sent);
        } else {
            log.debug("handleDownload: шаг 5 — remaining=0, файл уже актуален у клиента");
        }

        // 6. Отправляем финальное подтверждение
        log.debug("handleDownload: шаг 6 — отправка финального подтверждения");
        IoUtils.writeLine(out, "Файл отправлен: " + filename);

        log.debug("handleDownload: завершение");
    }
}