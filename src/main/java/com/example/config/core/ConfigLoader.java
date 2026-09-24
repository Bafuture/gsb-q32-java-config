package com.example.config.core;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.config.binding.ConfigBinder;
import com.example.config.convert.TypeConverter;
import com.example.config.source.CommandLineConfigSource;
import com.example.config.source.ConfigSource;
import com.example.config.source.EnvironmentConfigSource;
import com.example.config.source.FilePropertiesConfigSource;
import com.example.config.source.MapConfigSource;
import com.example.config.validation.Validator;

/**
 * 配置加载器：按固定优先级组装四个来源（低 -&gt; 高）：
 * <pre>
 *   代码内默认值 &lt; 配置文件 &lt; 环境变量 &lt; 启动参数
 * </pre>
 * 高优先级来源中的同名字符串值整体覆盖低优先级来源。
 */
public class ConfigLoader {

    private final List<ConfigSource> sources;
    private final TypeConverter converter;
    private final Validator validator;

    public ConfigLoader(List<ConfigSource> sources) {
        this(sources, new TypeConverter(), new Validator());
    }

    public ConfigLoader(List<ConfigSource> sources, TypeConverter converter, Validator validator) {
        this.sources = List.copyOf(sources);
        this.converter = converter;
        this.validator = validator;
    }

    public <T> T load(Class<T> type) {
        return new ConfigBinder(sources, converter, validator).bind(type);
    }

    public List<ConfigSource> sources() {
        return sources;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 便捷构造：默认值 -&gt; 配置文件 -&gt; 环境变量 -&gt; 启动参数。 */
    public static <T> T load(Class<T> type, Map<String, String> defaults, Path configFile, String[] args) {
        return builder()
                .defaults(defaults)
                .configFile(configFile)
                .environment()
                .commandLine(args)
                .build()
                .load(type);
    }

    public static final class Builder {

        private final List<ConfigSource> sources = new ArrayList<>();
        private TypeConverter converter = new TypeConverter();
        private Validator validator = new Validator();
        private Path configFile;
        private Charset charset = StandardCharsets.UTF_8;

        /** 代码内默认值（最低优先级）。 */
        public Builder defaults(Map<String, String> defaults) {
            sources.add(new MapConfigSource("代码内默认值", defaults == null ? Map.of() : defaults));
            return this;
        }

        public Builder source(ConfigSource source) {
            sources.add(source);
            return this;
        }

        /** Properties 配置文件。文件不存在时视为空来源；格式错误在加载时抛出。 */
        public Builder configFile(Path file) {
            this.configFile = file;
            if (file != null) {
                sources.add(new FilePropertiesConfigSource(file, charset));
            }
            return this;
        }

        public Builder charset(Charset charset) {
            this.charset = charset;
            return this;
        }

        /** 真实进程环境变量。 */
        public Builder environment() {
            sources.add(new EnvironmentConfigSource());
            return this;
        }

        /** 指定环境变量映射（主要用于测试）。 */
        public Builder environment(Map<String, String> env) {
            sources.add(new EnvironmentConfigSource(env == null ? Map.of() : env));
            return this;
        }

        /** 启动参数（最高优先级）。 */
        public Builder commandLine(String[] args) {
            sources.add(new CommandLineConfigSource(args));
            return this;
        }

        public Builder typeConverter(TypeConverter converter) {
            this.converter = converter;
            return this;
        }

        public Builder validator(Validator validator) {
            this.validator = validator;
            return this;
        }

        public Path configFile() {
            return configFile;
        }

        public ConfigLoader build() {
            return new ConfigLoader(new ArrayList<>(sources), converter, validator);
        }

        public <T> T load(Class<T> type) {
            return build().load(type);
        }
    }

    /** 供 ConfigManager 重新加载：用全新的文件来源替换旧文件来源，其它来源原样保留。 */
    ConfigLoader reloadFileSources(Path file, Charset charset) {
        List<ConfigSource> rebuilt = new ArrayList<>();
        boolean replaced = false;
        for (ConfigSource source : sources) {
            if (source instanceof FilePropertiesConfigSource fileSource && fileSource.getFile().equals(file)) {
                rebuilt.add(new FilePropertiesConfigSource(file, charset));
                replaced = true;
            } else {
                rebuilt.add(source);
            }
        }
        if (!replaced) {
            rebuilt.add(new FilePropertiesConfigSource(file, charset));
        }
        return new ConfigLoader(rebuilt, converter, validator);
    }

    /** 测试辅助：构造默认值 Map。 */
    public static Map<String, String> mapOf(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
