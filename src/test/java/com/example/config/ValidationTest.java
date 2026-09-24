package com.example.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.config.annotation.Pattern;
import com.example.config.annotation.Range;
import com.example.config.annotation.Required;
import com.example.config.core.ConfigLoader;
import com.example.config.exception.ConfigBindingException;

class ValidationTest {

    public static class ServiceConfig {
        @Required
        public String serviceName;

        @Range(min = 1, max = 65535)
        public int port = 8080;

        @Pattern(regexp = "[a-z0-9-]+")
        public String region = "cn-east";

        @Required
        @Pattern(regexp = "\\d{1,3}(\\.\\d{1,3}){3}")
        public String host;
    }

    @Test
    void validConfigBindsSuccessfully() {
        ServiceConfig config = ConfigLoader.builder()
                .defaults(Map.of("service-name", "billing", "host", "10.0.0.1", "port", "9000"))
                .build()
                .load(ServiceConfig.class);

        assertThat(config.serviceName).isEqualTo("billing");
        assertThat(config.host).isEqualTo("10.0.0.1");
        assertThat(config.port).isEqualTo(9000);
    }

    @Test
    void collectsAllViolationsInsteadOfFailingFast() {
        assertThatThrownBy(() -> ConfigLoader.builder()
                .defaults(Map.of(
                        "port", "70000",
                        "region", "Bad Region!",
                        "host", "not-an-ip"))
                .build()
                .load(ServiceConfig.class))
                .isInstanceOfSatisfying(ConfigBindingException.class, ex -> {
                    List<String> violations = ex.getViolations();
                    assertThat(violations).anyMatch(v -> v.contains("配置键=service-name") && v.contains("必填"));
                    assertThat(violations).anyMatch(v -> v.contains("配置键=port") && v.contains("超出允许范围"));
                    assertThat(violations).anyMatch(v -> v.contains("配置键=region") && v.contains("不匹配正则"));
                    assertThat(violations).anyMatch(v -> v.contains("配置键=host") && v.contains("不匹配正则"));
                    assertThat(violations).hasSize(4);
                });
    }

    @Test
    void blankStringCountsAsMissing() {
        assertThatThrownBy(() -> ConfigLoader.builder()
                .defaults(Map.of("service-name", "   "))
                .build()
                .load(ServiceConfig.class))
                .isInstanceOf(ConfigBindingException.class)
                .hasMessageContaining("配置键=service-name");
    }

    public static class PoolConfig {
        @Required
        public List<String> urls;
    }

    @Test
    void emptyCollectionFailsRequired() {
        assertThatThrownBy(() -> ConfigLoader.builder()
                .defaults(Map.of("urls", ""))
                .build()
                .load(PoolConfig.class))
                .isInstanceOf(ConfigBindingException.class)
                .hasMessageContaining("配置键=urls")
                .hasMessageContaining("必填");
    }

    public static class NestedConfig {
        public static class DbConfig {
            @Required
            public String url;
        }

        @Required
        public DbConfig db = new DbConfig();
    }

    @Test
    void nestedRequiredViolationUsesFullKeyPath() {
        assertThatThrownBy(() -> ConfigLoader.builder()
                .defaults(Map.of("db.url", ""))
                .build()
                .load(NestedConfig.class))
                .isInstanceOf(ConfigBindingException.class)
                .hasMessageContaining("配置键=db.url");
    }

    @Test
    void conversionAndValidationErrorsAreReportedTogether() {
        assertThatThrownBy(() -> ConfigLoader.builder()
                .defaults(Map.of(
                        "port", "not-a-number",
                        "region", "Invalid!"))
                .build()
                .load(ServiceConfig.class))
                .isInstanceOfSatisfying(ConfigBindingException.class, ex -> {
                    assertThat(ex.getConversionErrors()).hasSize(1);
                    assertThat(ex.getConversionErrors().get(0).getKey()).isEqualTo("port");
                    assertThat(ex.getViolations())
                            .anyMatch(v -> v.contains("配置键=service-name"))
                            .anyMatch(v -> v.contains("配置键=region"));
                });
    }
}
