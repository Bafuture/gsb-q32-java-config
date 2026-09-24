package com.example.config.source;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 启动参数来源，优先级最高。
 * 支持的形式：{@code --key=value}、{@code --key value}、{@code -Dkey=value}（JVM 系统属性风格）。
 */
public class CommandLineConfigSource implements ConfigSource {

    private final Map<String, String> values;

    public CommandLineConfigSource(String[] args) {
        this.values = new LinkedHashMap<>();
        if (args == null) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            String keyValue = null;
            if (arg.startsWith("--")) {
                keyValue = arg.substring(2);
            } else if (arg.startsWith("-D")) {
                keyValue = arg.substring(2);
            } else {
                continue;
            }
            int eq = keyValue.indexOf('=');
            if (eq >= 0) {
                put(keyValue.substring(0, eq), keyValue.substring(eq + 1));
            } else if (i + 1 < args.length && !args[i + 1].startsWith("--") && !args[i + 1].startsWith("-D")) {
                put(keyValue, args[++i]);
            } else {
                put(keyValue, "");
            }
        }
    }

    private void put(String key, String value) {
        if (!key.isEmpty()) {
            values.put(Keys.normalize(key), value);
        }
    }

    @Override
    public String name() {
        return "启动参数";
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(values.get(Keys.normalize(key)));
    }

    @Override
    public Map<String, String> entries() {
        return Collections.unmodifiableMap(values);
    }
}
