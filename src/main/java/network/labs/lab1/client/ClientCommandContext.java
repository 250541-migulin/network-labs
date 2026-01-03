package network.labs.lab1.client;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

/**
 * Контекст выполнения команды на клиенте.
 *
 * @param in       поток ввода от сервера
 * @param out      поток вывода к серверу
 * @param clientDir директория для хранения файлов клиента
 */
public record ClientCommandContext(InputStream in, OutputStream out, Path clientDir) {}
