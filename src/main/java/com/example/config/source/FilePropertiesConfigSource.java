package com.example.config.source;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Optional;

import com.example.config.parse.PropertiesParser;

/**
 * Properties 配置文件来源。文件不存在是合法的（返回空来源）；
 * 文件存在但格式错误会抛出 {@link com.example.config.exception.ConfigParseException}。
 */
public class FilePropertiesConfigSource implements ConfigSource {

    private final Path file;
    private final Charset charset;
    private final java.util.Map<String, String> values;

    public FilePropertiesConfigSource(Path file) {
        this(file, java.nio.charset.StandardCharsets.UTF_8);
    }

    public FilePropertiesConfigSource(Path file, Charset charset) {
        this.file = file;
        this.charset = charset;
        this.values = PropertiesParser.parse(file, charset);
    }

    public Path getFile() {
        return file;
    }

    @Override
    public String name() {
        return "配置文件(" + file + ")";
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(values.get(Keys.normalize(key)));
    }

    @Override
    public java.util.Map<String, String> entries() {
        return java.util.Collections.unmodifiableMap(values);
    }
}
