package network.labs.lab1.common;

public enum CommandName {
    ECHO("ECHO"),
    TIME("TIME"),
    UPLOAD("UPLOAD"),
    DOWNLOAD("DOWNLOAD"),
    CLOSE("CLOSE"),
    UNKNOWN("UNKNOWN");

    private final String key;
    CommandName(String key) { this.key = key; }
    public String key() { return key; }
}