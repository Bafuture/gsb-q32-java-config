package com.example.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.config.convert.TypeConverter;
import com.example.config.core.ConfigLoader;
import com.example.config.exception.ConfigBindingException;
import com.example.config.exception.ConversionException;

class TypeConversionTest {

    public enum Level { TRACE, DEBUG, INFO, WARN, ERROR }

    public static class TypesConfig {
        public boolean enabled;
        public int intValue;
        public long longValue;
        public double doubleValue;
        public Level level;
        public Duration timeout;
        public Duration interval;
        public Path logFile;
        public List<String> tags;
        public List<Integer> ports;
        public List<Level> levels;
    }

    private TypesConfig load(Map<String, String> values) {
        return ConfigLoader.builder().defaults(values).build().load(TypesConfig.class);
    }

    @Test
    void convertsPrimitivesEnumsDurationsPathsAndLists() {
        TypesConfig config = load(Map.ofEntries(
                Map.entry("enabled", "yes"),
                Map.entry("int-value", "-17"),
                Map.entry("long-value", "9999999999"),
                Map.entry("double-value", "3.14"),
                Map.entry("level", "warn"),
                Map.entry("timeout", "30s"),
                Map.entry("interval", "PT5M"),
                Map.entry("log-file", "/var/log/app.log"),
                Map.entry("tags", "web, prod , v2"),
                Map.entry("ports", "8080, 8081,8082"),
                Map.entry("levels", "info, error")));

        assertThat(config.enabled).isTrue();
        assertThat(config.intValue).isEqualTo(-17);
        assertThat(config.longValue).isEqualTo(9_999_999_999L);
        assertThat(config.doubleValue).isEqualTo(3.14);
        assertThat(config.level).isEqualTo(Level.WARN);
        assertThat(config.timeout).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.interval).isEqualTo(Duration.ofMinutes(5));
        assertThat(config.logFile).isEqualTo(Paths.get("/var/log/app.log"));
        assertThat(config.tags).containsExactly("web", "prod", "v2");
        assertThat(config.ports).containsExactly(8080, 8081, 8082);
        assertThat(config.levels).containsExactly(Level.INFO, Level.ERROR);
    }

    @Test
    void convertsAllDurationUnits() {
        TypeConverter converter = new TypeConverter();
        Duration millis = converter.convert("k", "500ms", Duration.class);
        Duration seconds = converter.convert("k", "2s", Duration.class);
        Duration minutes = converter.convert("k", "5m", Duration.class);
        Duration hours = converter.convert("k", "2h", Duration.class);
        Duration days = converter.convert("k", "1d", Duration.class);
        assertThat(millis).isEqualTo(Duration.ofMillis(500));
        assertThat(seconds).isEqualTo(Duration.ofSeconds(2));
        assertThat(minutes).isEqualTo(Duration.ofMinutes(5));
        assertThat(hours).isEqualTo(Duration.ofHours(2));
        assertThat(days).isEqualTo(Duration.ofDays(1));
    }

    @Test
    void badIntegerReportsKeyExpectedTypeAndRawValue() {
        assertThatThrownBy(() -> load(Map.of("int-value", "abc")))
                .isInstanceOf(ConfigBindingException.class)
                .hasMessageContaining("配置键=int-value")
                .hasMessageContaining("期望类型=int")
                .hasMessageContaining("原始值='abc'");
    }

    @Test
    void badEnumReportsKeyExpectedTypeRawValueAndAllowedConstants() {
        assertThatThrownBy(() -> load(Map.of("level", "VERBOSE")))
                .isInstanceOf(ConfigBindingException.class)
                .hasMessageContaining("配置键=level")
                .hasMessageContaining("期望类型=枚举 Level")
                .hasMessageContaining("原始值='VERBOSE'")
                .hasMessageContaining("TRACE, DEBUG, INFO, WARN, ERROR");
    }

    @Test
    void badDurationReportsKeyExpectedTypeAndRawValue() {
        assertThatThrownBy(() -> load(Map.of("timeout", "soon")))
                .isInstanceOf(ConfigBindingException.class)
                .hasMessageContaining("配置键=timeout")
                .hasMessageContaining("期望类型=Duration")
                .hasMessageContaining("原始值='soon'");
    }

    @Test
    void badBooleanReportsKeyExpectedTypeAndRawValue() {
        assertThatThrownBy(() -> load(Map.of("enabled", "maybe")))
                .isInstanceOf(ConfigBindingException.class)
                .hasMessageContaining("配置键=enabled")
                .hasMessageContaining("期望类型=boolean")
                .hasMessageContaining("原始值='maybe'");
    }

    @Test
    void badListElementReportsTheListKey() {
        assertThatThrownBy(() -> load(Map.of("ports", "8080, nope")))
                .isInstanceOf(ConfigBindingException.class)
                .hasMessageContaining("配置键=ports")
                .hasMessageContaining("期望类型=Integer")
                .hasMessageContaining("原始值='nope'");
    }

    @Test
    void directConverterThrowsConversionException() {
        TypeConverter converter = new TypeConverter();
        assertThatThrownBy(() -> converter.convert("level", "VERBOSE", Level.class))
                .isInstanceOf(ConversionException.class)
                .hasMessageContaining("配置键=level")
                .hasMessageContaining("期望类型=枚举 Level");
    }

    @Test
    void indexedScalarListIsAlsoConverted() {
        TypesConfig config = load(Map.of(
                "ports[0]", "9001",
                "ports[1]", "9002"));
        assertThat(config.ports).containsExactly(9001, 9002);
    }
}
