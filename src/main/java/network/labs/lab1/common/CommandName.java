package network.labs.lab1.common;

public enum CommandName {
    TIME,
    UPLOAD,
    DOWNLOAD,
    CLOSE,
    ECHO,
    UNKNOWN;

    public String key() {
        return this.name().toUpperCase();
    }
}
