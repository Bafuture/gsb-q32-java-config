package com.example.config.exception;

import java.nio.file.Path;

/**
 * 配置文件格式错误。必须携带文件路径、行号与原因。
 */
public class ConfigParseException extends ConfigException {

    private final String location;
    private final int lineNumber;

    public ConfigParseException(Path file, int lineNumber, String reason) {
        this(file == null ? "<inline>" : file.toString(), lineNumber, reason);
    }

    public ConfigParseException(String location, int lineNumber, String reason) {
        super(buildMessage(location, lineNumber, reason));
        this.location = location;
        this.lineNumber = lineNumber;
    }

    public ConfigParseException(Path file, int lineNumber, String reason, Throwable cause) {
        super(buildMessage(file == null ? "<inline>" : file.toString(), lineNumber, reason), cause);
        this.location = file == null ? "<inline>" : file.toString();
        this.lineNumber = lineNumber;
    }

    private static String buildMessage(String location, int lineNumber, String reason) {
        return "配置文件格式错误: 文件=" + location + ", 行号=" + lineNumber + ", 原因=" + reason;
    }

    public String getLocation() {
        return location;
    }

    public int getLineNumber() {
        return lineNumber;
    }
}
