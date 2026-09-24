package com.example.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.config.annotation.Range;
import com.example.config.annotation.Required;
import com.example.config.core.ConfigListener;
import com.example.config.core.ConfigLoader;
import com.example.config.core.ConfigManager;
import com.example.config.exception.ConfigParseException;

class RefreshTest {

    public static class AppConfig {
        @Required
        public String name;

        @Range(min = 1, max = 65535)
        public int port = 8080;
    }

    private Path writeFile(Path dir, String name, String body) throws Exception {
        Path file = dir.resolve(name);
        Files.writeString(file, body);
        // 显式推进修改时间，规避部分文件系统秒级时间粒度
        Files.setLastModifiedTime(file, FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 1000));
        return file;
    }

    private ConfigManager<AppConfig> manager(Path file, Map<String, String> env, String[] args) {
        ConfigLoader loader = ConfigLoader.builder()
                .defaults(Map.of("name", "default"))
                .configFile(file)
                .environment(env)
                .commandLine(args)
                .build();
        return new ConfigManager<>(AppConfig.class, loader);
    }

    @Test
    void successfulRefreshReplacesConfigAndNotifiesListeners(@TempDir Path dir) throws Exception {
        Path file = writeFile(dir, "app.properties", "name=v1\nport=9001\n");
        ConfigManager<AppConfig> manager = manager(file, Map.of(), new String[0]);
        AppConfig first = manager.init();
        assertThat(first.name).isEqualTo("v1");

        List<Object> reloaded = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        manager.addListener(new ConfigListener() {
            @Override
            public void onReload(Object newConfig) {
                reloaded.add(newConfig);
            }

            @Override
            public void onError(Throwable error) {
                errors.add(error);
            }
        });

        writeFile(dir, "app.properties", "name=v2\nport=9002\n");
        AppConfig second = manager.refresh();

        assertThat(second.name).isEqualTo("v2");
        assertThat(second.port).isEqualTo(9002);
        assertThat(manager.getConfig()).isSameAs(second);
        assertThat(reloaded).hasSize(1);
        assertThat(errors).isEmpty();
        manager.close();
    }

    @Test
    void failedRefreshKeepsOldConfigAndReportsError(@TempDir Path dir) throws Exception {
        Path file = writeFile(dir, "app.properties", "name=good\nport=9001\n");
        ConfigManager<AppConfig> manager = manager(file, Map.of(), new String[0]);
        AppConfig first = manager.init();

        AtomicReference<Throwable> reported = new AtomicReference<>();
        manager.addListener(new ConfigListener() {
            @Override
            public void onError(Throwable error) {
                reported.set(error);
            }
        });

        // 写入格式错误的文件（缺少键）
        Files.writeString(file, "name=still-good\n=broken-line\n");
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 2000));

        assertThatThrownBy(manager::refresh).isInstanceOf(ConfigParseException.class);

        // 旧配置必须保持不变
        assertThat(manager.getConfig()).isSameAs(first);
        assertThat(manager.getConfig().name).isEqualTo("good");
        assertThat(reported.get()).isInstanceOf(ConfigParseException.class);

        // 文件修好后可以再次刷新成功
        writeFile(dir, "app.properties", "name=recovered\nport=9003\n");
        AppConfig recovered = manager.refresh();
        assertThat(recovered.name).isEqualTo("recovered");
        assertThat(manager.getConfig()).isSameAs(recovered);
        manager.close();
    }

    @Test
    void validationFailureOnRefreshAlsoKeepsOldConfig(@TempDir Path dir) throws Exception {
        Path file = writeFile(dir, "app.properties", "name=good\nport=9001\n");
        ConfigManager<AppConfig> manager = manager(file, Map.of(), new String[0]);
        AppConfig first = manager.init();

        writeFile(dir, "app.properties", "name=\nport=99999\n");

        assertThatThrownBy(manager::refresh).hasMessageContaining("配置绑定失败");
        assertThat(manager.getConfig()).isSameAs(first);
        assertThat(manager.getConfig().name).isEqualTo("good");
        manager.close();
    }

    @Test
    void fileWatcherReloadsOnChange(@TempDir Path dir) throws Exception {
        Path file = writeFile(dir, "app.properties", "name=w1\nport=9001\n");
        ConfigManager<AppConfig> manager = manager(file, Map.of(), new String[0]);
        manager.init();

        List<Object> reloaded = new ArrayList<>();
        AtomicReference<Throwable> errors = new AtomicReference<>();
        manager.addListener(new ConfigListener() {
            @Override
            public void onReload(Object newConfig) {
                reloaded.add(newConfig);
            }

            @Override
            public void onError(Throwable error) {
                errors.set(error);
            }
        });

        manager.startWatching(20, TimeUnit.MILLISECONDS);
        writeFile(dir, "app.properties", "name=w2\nport=9002\n");
        // 把 mtime 推到当前墙钟时间之后，规避文件属性读取缓存的影响
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 3000));

        long deadline = System.currentTimeMillis() + 10_000;
        while (reloaded.isEmpty() && errors.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        assertThat(reloaded).hasSize(1);
        assertThat(manager.getConfig().name).isEqualTo("w2");
        assertThat(errors.get()).isNull();
        manager.close();
    }

    @Test
    void highPrioritySourcesStillApplyAfterRefresh(@TempDir Path dir) throws Exception {
        Path file = writeFile(dir, "app.properties", "name=from-file\n");
        ConfigManager<AppConfig> manager = manager(file, Map.of("PORT", "6100"), new String[]{"--name=from-cli"});
        manager.init();

        assertThat(manager.getConfig().name).isEqualTo("from-cli");
        assertThat(manager.getConfig().port).isEqualTo(6100);

        writeFile(dir, "app.properties", "name=from-file-v2\nport=7100\n");
        AppConfig refreshed = manager.refresh();

        // 启动参数/环境变量优先级仍高于文件
        assertThat(refreshed.name).isEqualTo("from-cli");
        assertThat(refreshed.port).isEqualTo(6100);
        manager.close();
    }
}
