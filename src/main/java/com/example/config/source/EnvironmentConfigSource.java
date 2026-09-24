package com.example.config.source;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 环境变量来源。
 * 查询时把配置键转为环境变量名（大写 + 下划线）后在 System.getenv 中查找；
 * 列表下标发现通过把枚举到的环境变量名「还原」为配置键实现。
 */
public class EnvironmentConfigSource implements ConfigSource {

    private final Map<String, String> env;

    public EnvironmentConfigSource() {
        this(System.getenv());
    }

    public EnvironmentConfigSource(Map<String, String> env) {
        this.env = env;
    }

    @Override
    public String name() {
        return "环境变量";
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(env.get(Keys.envName(key)));
    }

    @Override
    public Map<String, String> entries() {
        Map<String, String> result = new LinkedHashMap<>();
        env.forEach((envKey, value) -> result.put(envKeyToConfigKey(envKey), value));
        return result;
    }

    /** ENV_VAR_NAME -&gt; env-var-name（无法还原 camelCase 边界，因此统一用 kebab-case）。 */
    static String envKeyToConfigKey(String envKey) {
        return envKey.toLowerCase().replace('_', '-');
    }
}
