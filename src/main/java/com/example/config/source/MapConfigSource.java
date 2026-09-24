package com.example.config.source;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 基于内存 Map 的配置来源，键会被规范化（camelCase -&gt; kebab-case）。
 * 用于「代码内默认值」以及测试。
 */
public class MapConfigSource implements ConfigSource {

    private final String sourceName;
    private final Map<String, String> values;

    public MapConfigSource(String sourceName, Map<String, String> values) {
        this.sourceName = sourceName;
        this.values = new LinkedHashMap<>();
        if (values != null) {
            values.forEach((k, v) -> this.values.put(Keys.normalize(k), v));
        }
    }

    @Override
    public String name() {
        return sourceName;
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
