package com.example.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.config.annotation.Required;
import com.example.config.core.ConfigLoader;

/**
 * 结构映射：嵌套对象、嵌套对象列表、字段名 camelCase -&gt; kebab-case。
 */
class BindingTest {

    public enum PoolType { FIXED, CACHED }

    public static class Endpoint {
        public String host;
        public int port;
        public Duration timeout;
    }

    public static class Pool {
        public PoolType type;
        public int maxSize;
        public List<String> labels;
    }

    public static class ServerConfig {
        public int serverPort;
        public Duration connectTimeout;
        public Endpoint primary = new Endpoint();
        public Endpoint backup = new Endpoint();
        public List<Endpoint> endpoints;
        public List<Pool> pools;
        public List<Integer> allowedPorts;
    }

    @Test
    void bindsNestedObjectsAndListsWithKebabKeys() {
        ServerConfig config = ConfigLoader.builder().defaults(Map.ofEntries(
                Map.entry("server-port", "8443"),
                Map.entry("connect-timeout", "10s"),
                Map.entry("primary.host", "10.0.0.1"),
                Map.entry("primary.port", "1111"),
                Map.entry("primary.timeout", "500ms"),
                Map.entry("backup.host", "10.0.0.2"),
                Map.entry("endpoints[0].host", "a.example.com"),
                Map.entry("endpoints[0].port", "80"),
                Map.entry("endpoints[1].host", "b.example.com"),
                Map.entry("endpoints[1].port", "81"),
                Map.entry("endpoints[1].timeout", "2m"),
                Map.entry("pools[0].type", "fixed"),
                Map.entry("pools[0].max-size", "8"),
                Map.entry("pools[0].labels", "green, canary"),
                Map.entry("allowed-ports", "80, 443")))
                .build()
                .load(ServerConfig.class);

        assertThat(config.serverPort).isEqualTo(8443);
        assertThat(config.connectTimeout).isEqualTo(Duration.ofSeconds(10));

        assertThat(config.primary.host).isEqualTo("10.0.0.1");
        assertThat(config.primary.port).isEqualTo(1111);
        assertThat(config.primary.timeout).isEqualTo(Duration.ofMillis(500));
        assertThat(config.backup.host).isEqualTo("10.0.0.2");

        assertThat(config.endpoints).hasSize(2);
        assertThat(config.endpoints.get(0).host).isEqualTo("a.example.com");
        assertThat(config.endpoints.get(0).port).isEqualTo(80);
        assertThat(config.endpoints.get(1).host).isEqualTo("b.example.com");
        assertThat(config.endpoints.get(1).timeout).isEqualTo(Duration.ofMinutes(2));

        assertThat(config.pools).hasSize(1);
        assertThat(config.pools.get(0).type).isEqualTo(PoolType.FIXED);
        assertThat(config.pools.get(0).maxSize).isEqualTo(8);
        assertThat(config.pools.get(0).labels).containsExactly("green", "canary");

        assertThat(config.allowedPorts).containsExactly(80, 443);
    }

    @Test
    void nestedKeysCanBeOverriddenByEnvironment() {
        Map<String, String> env = Map.of(
                "PRIMARY_HOST", "env.example.com",
                "ENDPOINTS_0_PORT", "9999");

        ServerConfig config = ConfigLoader.builder()
                .defaults(Map.of(
                        "primary.host", "default.example.com",
                        "primary.port", "1000",
                        "endpoints[0].host", "n0.example.com",
                        "endpoints[0].port", "8000"))
                .environment(env)
                .build()
                .load(ServerConfig.class);

        assertThat(config.primary.host).isEqualTo("env.example.com");
        assertThat(config.primary.port).isEqualTo(1000);
        assertThat(config.endpoints.get(0).port).isEqualTo(9999);
        assertThat(config.endpoints.get(0).host).isEqualTo("n0.example.com");
    }

    @Test
    void fieldInitializersRemainWhenUnconfigured() {
        ServerConfig config = ConfigLoader.builder().defaults(Map.of()).build().load(ServerConfig.class);
        assertThat(config.primary).isNotNull();
        assertThat(config.serverPort).isZero();
    }

    public static class ParentConfig {
        public static class Child {
            @Required
            public String name;
        }

        public Child child;
    }

    @Test
    void nestedObjectsAreInstantiatedAutomatically() {
        ParentConfig config = ConfigLoader.builder()
                .defaults(Map.of("child.name", "baby"))
                .build()
                .load(ParentConfig.class);
        assertThat(config.child).isNotNull();
        assertThat(config.child.name).isEqualTo("baby");
    }
}
