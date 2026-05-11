package network.labs.lab1.client;

import network.labs.lab1.common.Config;
import network.labs.lab1.common.IoUtils;
import network.labs.lab1.common.NetworkUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

/**
 * TCP-клиент для Лабораторной работы №1.
 * Реализует интерактивный интерфейс, обработку команд и корректное
 * обнаружение разрыва соединения со стороны сервера.
 */
public final class TcpClient {

    /**
     * Запускает клиент: подключается к серверу и входит в цикл обработки команд.
     */
    public void start(String host, int port) {
        try (Socket socket = new Socket(host, port)) {
            System.out.println("Подключено к " + host + ":" + port);
            // Требование ЛР: контроль живучести соединения + таймаут на операции
            socket.setKeepAlive(true);
            socket.setSoTimeout(Config.SOCKET_TIMEOUT_MS);

            try (InputStream in = socket.getInputStream();
                 OutputStream out = socket.getOutputStream()) {
                runCommandLoop(in, out);
            }
        } catch (SocketException e) {
            System.out.println("\n Соединение разорвано: " + e.getMessage());
            System.out.println("Для продолжения запустите клиента заново.");
        } catch (IOException e) {
            System.err.println("Ошибка подключения: " + e.getMessage());
        }
    }

    /**
     * Основной цикл чтения пользовательского ввода и отправки команд.
     */
    private void runCommandLoop(InputStream in, OutputStream out) throws IOException {
        Scanner scanner = new Scanner(System.in, "UTF-8");
        System.out.println("Доступные команды: ECHO, TIME, CLOSE, UPLOAD <файл>, DOWNLOAD <файл>");

        while (true) {
            System.out.print("> ");
            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            String response = processCommand(line, in, out);
            if ("CLOSE".equals(extractCommand(line))) {
                break;
            }
            if (response != null) {
                System.out.println("Сервер: " + response);
            }
        }
    }

    /**
     * Распознаёт команду и делегирует обработку.
     * Для неизвестных команд запрос всё равно отправляется на сервер,
     * чтобы сервер сам вернул ошибку. Это убирает ложный префикс "Сервер:".
     */
    private String processCommand(String line, InputStream in, OutputStream out) throws IOException {
        String cmd = extractCommand(line);
        switch (cmd) {
            case "ECHO":
            case "TIME":
            case "CLOSE":
                return handleSimpleCommand(line, in, out);
            case "UPLOAD":
                handleUpload(line, in, out);
                return null;
            case "DOWNLOAD":
                handleDownload(line, in, out);
                return null;
            default:
                // Отправляем неизвестную команду на сервер. Сервер ответит "Error: unknown command"
                return handleSimpleCommand(line, in, out);
        }
    }

    private String extractCommand(String line) {
        return line.split("\\s+")[0].toUpperCase();
    }

    /**
     * Отправляет команду и возвращает ответ.
     * Если readLine вернул null, генерирует понятное исключение.
     */
    private String handleSimpleCommand(String line, InputStream in, OutputStream out) throws IOException {
        IoUtils.writeLine(out, line);
        String response = IoUtils.readLine(in);
        if (response == null) {
            throw new SocketException("Сервер закрыл соединение");
        }
        return response;
    }

    /**
     * Обрабатывает команду UPLOAD.
     * Протокол обмена:
     * 1. Клиент -> Сервер: UPLOAD <файл> [--force]\r\n
     * 2. Сервер -> Клиент: OK <offset>\r\n
     * 3. Клиент -> Сервер: <remaining>\r\n
     * 4. Клиент -> Сервер: [бинарные данные файла]
     * 5. Сервер -> Клиент: Финальный ответ\r\n
     */
    private void handleUpload(String line, InputStream in, OutputStream out) throws IOException {
        String[] tokens = line.split("\\s+");
        if (tokens.length < 2) { System.out.println("Использование: UPLOAD <файл> [--force]"); return; }

        String fname = tokens[1];
        Path src = Config.SOURCE_DIR.resolve(fname);
        if (!Files.exists(src)) { System.out.println("Файл не найден: " + src); return; }

        // 1. Отправляем команду
        IoUtils.writeLine(out, line);

        // 2. Читаем смещение, с которого сервер ожидает данные
        long offset = readServerOffset(in);
        if (offset < 0) return;

        long fileSize = Files.size(src);
        long remaining = fileSize - offset;

        // 3. Если файл уже полностью загружен, сообщаем размер 0 и выходим
        if (remaining <= 0) {
            IoUtils.writeLine(out, "0");
            System.out.println("Сервер: " + IoUtils.readLine(in));
            return;
        }

        // 4. КРИТИЧНО: отправляем серверу размер оставшихся байт ДО начала передачи потока
        IoUtils.writeLine(out, String.valueOf(remaining));

        System.out.println((offset > 0 ? "Докачка" : "Отправка") + ": " + fname +
                " (осталось: " + remaining + " байт, всего: " + fileSize + " байт)...");

        // 5. Передаём файл и считаем скорость
        long t0 = System.currentTimeMillis();
        try {
            long sent = IoUtils.copyFileToStream(src, out, offset);
            long elapsed = System.currentTimeMillis() - t0;
            System.out.printf("Отправлено: 100%% | Скорость: %.1f Мбит/с%n", NetworkUtils.calcSpeedMbps(sent, elapsed));
        } catch (SocketTimeoutException e) {
            System.out.println("Передача прервана: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Ошибка сети: " + e.getMessage());
        }

        // 6. Читаем финальный ответ сервера
        String resp = IoUtils.readLine(in);
        System.out.println("Сервер: " + (resp != null ? resp : "Соединение разорвано"));
    }

    /**
     * DOWNLOAD. Протокол:
     * 1. C -> S: DOWNLOAD <файл>\r\n
     * 2. S -> C: OK\r\n
     * 3. C -> S: <localSize>\r\n
     * 4. S -> C: <remaining>\r\n
     * 5. Если remaining > 0: S шлёт бинарные данные, затем финальный статус
     * 6. Если remaining == 0: S сразу шлёт финальный статус
     */
    private void handleDownload(String line, InputStream in, OutputStream out) throws IOException {
        String[] t = line.split("\\s+");
        if (t.length < 2) { System.out.println("Использование: DOWNLOAD <файл>"); return; }

        Path tgt = Config.TMP_DIR.resolve("dl_" + t[1]);
        long localSize = Files.exists(tgt) ? Files.size(tgt) : 0;

        IoUtils.writeLine(out, line);
        String status = IoUtils.readLine(in);
        if (status == null || !"OK".equals(status)) {
            System.out.println("Сервер: " + (status != null ? status : "Нет ответа"));
            return;
        }

        IoUtils.writeLine(out, String.valueOf(localSize));
        long remaining = readServerRemaining(in);

        // Если сервер говорит, что скачивать нечего, читаем его финальный статус и выходим
        if (remaining <= 0) {
            String resp = IoUtils.readLine(in);
            System.out.println(resp != null ? resp : "Файл уже актуален");
            return;
        }

        System.out.printf("Приём: %s (осталось: %,d байт)%n", t[1], remaining);
        long t0 = System.currentTimeMillis();
        try {
            long recv = IoUtils.copyStreamToFile(in, tgt, localSize > 0, remaining);
            System.out.printf("Готово | Скорость: %.1f Мбит/с%n", NetworkUtils.calcSpeedMbps(recv, System.currentTimeMillis() - t0));
        } catch (SocketTimeoutException e) { System.out.println("⏱️ Таймаут приёма"); }
        catch (IOException e) { System.out.println("🌐 Ошибка сети: " + e.getMessage()); }

        String resp = IoUtils.readLine(in);
        System.out.println("Сервер: " + (resp != null ? resp : "Соединение разорвано"));
        System.out.println("Сохранён: " + tgt.getFileName());
    }

    private String parseFilename(String line) {
        String[] parts = line.split("\\s+", 2);
        return (parts.length >= 2) ? parts[1] : null;
    }

    private long readServerOffset(InputStream in) throws IOException {
        String response = IoUtils.readLine(in);
        if (response == null) {
            throw new SocketException("Сервер закрыл соединение");
        }
        if (!response.startsWith("OK ")) {
            System.out.println("Сервер: " + response);
            return -1;
        }
        try {
            return Long.parseLong(response.substring(3).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private long readServerRemaining(InputStream in) throws IOException {
        String line = IoUtils.readLine(in);
        if (line == null) {
            throw new SocketException("Сервер закрыл соединение");
        }
        try {
            return Long.parseLong(line.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}