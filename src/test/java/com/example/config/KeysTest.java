package com.example.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.config.source.Keys;

class KeysTest {

    @Test
    void camelToKebabHandlesWordsAndAcronyms() {
        assertThat(Keys.camelToKebab("port")).isEqualTo("port");
        assertThat(Keys.camelToKebab("serverPort")).isEqualTo("server-port");
        assertThat(Keys.camelToKebab("connectTimeoutMs")).isEqualTo("connect-timeout-ms");
        assertThat(Keys.camelToKebab("URLPath")).isEqualTo("url-path");
        assertThat(Keys.camelToKebab("maxHTTPConnections"))
                .isEqualTo("max-http-connections");
    }

    @Test
    void normalizeLeavesDotsAndIndicesUntouched() {
        assertThat(Keys.normalize("server.serverPort")).isEqualTo("server.server-port");
        assertThat(Keys.normalize("endpoints[0].hostName")).isEqualTo("endpoints[0].host-name");
        assertThat(Keys.normalize("connectTimeoutMs")).isEqualTo("connect-timeout-ms");
    }

    @Test
    void envNameUsesUpperSnakeCaseWithIndices() {
        assertThat(Keys.envName("server.port")).isEqualTo("SERVER_PORT");
        assertThat(Keys.envName("connect-timeout")).isEqualTo("CONNECT_TIMEOUT");
        assertThat(Keys.envName("endpoints[0].host")).isEqualTo("ENDPOINTS_0_HOST");
        assertThat(Keys.envName("primary.port")).isEqualTo("PRIMARY_PORT");
    }

    @Test
    void joinHandlesEmptyPrefix() {
        assertThat(Keys.join("", "name")).isEqualTo("name");
        assertThat(Keys.join("server", "port")).isEqualTo("server.port");
    }
}
