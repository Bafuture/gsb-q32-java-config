package com.example.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.config.core.ConfigLoader;

/**
 * 逐层验证优先级：代码内默认值 &lt; 配置文件 &lt; 环境变量 &lt; 启动参数。
 */
class PriorityTest {

    public static class AppConfig {
        public String name = "default-name";
        public int port = 8080;
        public boolean debug = false;
        public String host = "localhost";
    }

    private Path writeFile(@TempDir Path dir, String body) throws Exception {
        Path file = dir.resolve("app.properties");
        java.nio.file.Files.writeString(file, body);
        return file;
    }

    @Test
    void onlyDefaultsAreUsedWhenNoHigherSourceProvidesValue() {
        AppConfig config = ConfigLoader.builder()
                .defaults(Map.of("name", "code-name", "port", "9000"))
                .build()
                .load(AppConfig.class);

        assertThat(config.name).isEqualTo("code-name");
        assertThat(config.port).isEqualTo(9000);
        assertThat(config.debug).isFalse();
    }

    @Test
    void fileOverridesDefaultsKeyByKey(@TempDir Path dir) throws Exception {
        Path file = writeFile(dir, "name = file-name\nport = 7001\n");
        AppConfig config = ConfigLoader.builder()
                .defaults(Map.of("name", "code-name", "port", "9000", "debug", "true"))
                .configFile(file)
                .build()
                .load(AppConfig.class);

        // 文件覆盖了 name/port；debug 未出现在文件中，仍取默认值
        assertThat(config.name).isEqualTo("file-name");
        assertThat(config.port).isEqualTo(7001);
        assertThat(config.debug).isTrue();
    }

    @Test
    void environmentOverridesFileAndDefaults(@TempDir Path dir) throws Exception {
        Path file = writeFile(dir, "name = file-name\nport = 7001\n");
        Map<String, String> env = Map.of("NAME", "env-name", "HOST", "env-host");
        AppConfig config = ConfigLoader.builder()
                .defaults(Map.of("name", "code-name", "port", "9000"))
                .configFile(file)
                .environment(env)
                .build()
                .load(AppConfig.class);

        assertThat(config.name).isEqualTo("env-name");      // 环境变量覆盖文件
        assertThat(config.host).isEqualTo("env-host");      // 环境变量覆盖默认字段值
        assertThat(config.port).isEqualTo(7001);            // 环境变量未提供，保留文件值
    }

    @Test
    void commandLineOverridesEverything(@TempDir Path dir) throws Exception {
        Path file = writeFile(dir, "name = file-name\nport = 7001\n");
        Map<String, String> env = Map.of("NAME", "env-name", "PORT", "6001");
        String[] args = {"--name=cli-name", "--port=5001", "--debug=true"};

        AppConfig config = ConfigLoader.builder()
                .defaults(Map.of("name", "code-name", "port", "9000"))
                .configFile(file)
                .environment(env)
                .commandLine(args)
                .build()
                .load(AppConfig.class);

        assertThat(config.name).isEqualTo("cli-name");
        assertThat(config.port).isEqualTo(5001);
        assertThat(config.debug).isTrue();
    }

    @Test
    void commandLineAcceptsDStyleAndSpaceSeparatedArguments(@TempDir Path dir) throws Exception {
        Path file = writeFile(dir, "port = 7001\n");
        Map<String, String> env = Map.of("NAME", "env-name");
        String[] args = {"-Dname=cli-name", "--host", "cli-host"};

        AppConfig config = ConfigLoader.builder()
                .configFile(file)
                .environment(env)
                .commandLine(args)
                .build()
                .load(AppConfig.class);

        assertThat(config.name).isEqualTo("cli-name");
        assertThat(config.host).isEqualTo("cli-host");
    }

    @Test
    void sourcesAreOrderedLowToHigh() {
        var loader = ConfigLoader.builder()
                .defaults(Map.of("a", "1"))
                .environment(Map.of("B", "2"))
                .commandLine(new String[]{"--c=3"})
                .build();

        assertThat(loader.sources()).hasSize(3);
        assertThat(loader.sources().get(0).name()).contains("默认值");
        assertThat(loader.sources().get(1).name()).contains("环境变量");
        assertThat(loader.sources().get(2).name()).contains("启动参数");
    }
}
