package com.example.config.exception;

/**
 * 单个配置项类型转换失败。
 * 错误信息必须包含：配置键 + 期望类型 + 原始值。
 */
public class ConversionException extends ConfigException {

    private final String key;
    private final String expectedType;
    private final String rawValue;

    public ConversionException(String key, String expectedType, String rawValue, String reason) {
        super("配置类型转换失败: 配置键=" + key
                + ", 期望类型=" + expectedType
                + ", 原始值=" + quote(rawValue)
                + ", 原因=" + reason);
        this.key = key;
        this.expectedType = expectedType;
        this.rawValue = rawValue;
    }

    public ConversionException(String key, String expectedType, String rawValue, String reason, Throwable cause) {
        super("配置类型转换失败: 配置键=" + key
                + ", 期望类型=" + expectedType
                + ", 原始值=" + quote(rawValue)
                + ", 原因=" + reason, cause);
        this.key = key;
        this.expectedType = expectedType;
        this.rawValue = rawValue;
    }

    private static String quote(String value) {
        return value == null ? "null" : "'" + value + "'";
    }

    public String getKey() {
        return key;
    }

    public String getExpectedType() {
        return expectedType;
    }

    public String getRawValue() {
        return rawValue;
    }
}
