package com.example.config.convert;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简洁时长写法解析：数字 + 单位。
 * 单位：{@code ms} 毫秒、{@code s} 秒、{@code m} 分、{@code h} 时、{@code d} 天。
 * 不带单位时按 ISO-8601（如 {@code PT30S}）解析，均失败则抛出异常。
 */
final class DurationStyle {

    private static final Pattern SIMPLE = Pattern.compile("(?i)^\\s*([+-]?\\d+)\\s*(ms|s|m|h|d)?\\s*$");

    private DurationStyle() {
    }

    static Duration parse(String raw) {
        String text = raw.trim();
        Matcher matcher = SIMPLE.matcher(text);
        if (matcher.matches()) {
            long amount = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);
            if (unit == null) {
                throw new IllegalArgumentException("时长缺少单位（支持 ms/s/m/h/d）: " + raw);
            }
            switch (unit.toLowerCase()) {
                case "ms": return Duration.ofMillis(amount);
                case "s": return Duration.ofSeconds(amount);
                case "m": return Duration.ofMinutes(amount);
                case "h": return Duration.ofHours(amount);
                case "d": return Duration.ofDays(amount);
                default: throw new IllegalArgumentException("未知时长单位: " + unit);
            }
        }
        try {
            return Duration.parse(text);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("无法解析时长（形如 30s/5m/2h/500ms/1d 或 ISO-8601 PT30S）: " + raw);
        }
    }
}
