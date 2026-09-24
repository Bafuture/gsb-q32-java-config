package com.example.config.source;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 一个配置来源：能按配置键查字符串值，并能枚举自身所有键（用于列表下标发现）。
 * 实现类应当保证键的形式为规范化后的 kebab-case。
 */
public interface ConfigSource {

    /** 来源名称，用于错误信息与调试。 */
    String name();

    /** 按原始（或规范化）键查询，找不到返回 empty。 */
    Optional<String> get(String key);

    /** 枚举该来源中所有规范化键，默认实现返回空（不支持列表下标发现）。 */
    default Map<String, String> entries() {
        return new LinkedHashMap<>();
    }
}
