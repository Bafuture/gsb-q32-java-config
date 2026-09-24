package com.example.config.core;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.example.config.source.FilePropertiesConfigSource;

/**
 * 配置管理器：持有当前生效的配置对象，支持手动/自动动态刷新。
 *
 * <ul>
 *   <li>{@link #refresh()} 重新读取配置文件并重新绑定、校验；</li>
 *   <li>刷新成功：原子替换旧配置并通知 {@link ConfigListener#onReload}；</li>
 *   <li>刷新失败（文件格式错、转换错、校验错、IO 错）：保留旧配置，
 *       通知 {@link ConfigListener#onError}，并向上抛出异常；</li>
 *   <li>{@link #startWatching(long, TimeUnit)} 基于文件最后修改时间轮询自动刷新。</li>
 * </ul>
 */
public class ConfigManager<T> implements AutoCloseable {

    private final Class<T> type;
    private final ConfigLoader loader;
    private final Path configFile;
    private final Charset charset;
    private final List<ConfigListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicReference<T> current = new AtomicReference<>();
    private final AtomicReference<Long> lastModified = new AtomicReference<>();

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> watchTask;

    public ConfigManager(Class<T> type, ConfigLoader loader) {
        this(type, loader, resolveFile(loader), StandardCharsets.UTF_8);
    }

    public ConfigManager(Class<T> type, ConfigLoader loader, Path configFile, Charset charset) {
        this.type = Objects.requireNonNull(type, "type");
        this.loader = Objects.requireNonNull(loader, "loader");
        this.configFile = configFile;
        this.charset = charset == null ? StandardCharsets.UTF_8 : charset;
    }

    private static Path resolveFile(ConfigLoader loader) {
        for (com.example.config.source.ConfigSource source : loader.sources()) {
            if (source instanceof FilePropertiesConfigSource fileSource) {
                return fileSource.getFile();
            }
        }
        return null;
    }

    /** 首次加载：失败时直接抛出（此时还没有旧配置可保留）。 */
    public T init() {
        T loaded = loader.load(type);
        current.set(loaded);
        lastModified.set(currentMtime());
        return loaded;
    }

    public T getConfig() {
        T snapshot = current.get();
        if (snapshot == null) {
            throw new IllegalStateException("配置尚未加载，请先调用 init()");
        }
        return snapshot;
    }

    /**
     * 重新加载配置文件并绑定。
     * 成功时替换当前配置并通知监听器；失败时保留旧配置、通知错误并重新抛出异常。
     */
    public synchronized T refresh() {
        T old = current.get();
        try {
            ConfigLoader reloaded = configFile == null
                    ? loader
                    : loader.reloadFileSources(configFile, charset);
            T fresh = reloaded.load(type);
            current.set(fresh);
            lastModified.set(currentMtime());
            notifyReload(fresh);
            return fresh;
        } catch (RuntimeException error) {
            notifyError(error);
            if (old != null) {
                current.set(old);
            }
            throw error;
        }
    }

    /** 启动基于最后修改时间的轮询监听，发现变化时自动 {@link #refresh()}。 */
    public synchronized void startWatching(long period, TimeUnit unit) {
        if (configFile == null) {
            throw new IllegalStateException("未配置配置文件，无法监听变化");
        }
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "config-file-watcher");
                thread.setDaemon(true);
                return thread;
            });
        }
        if (watchTask != null) {
            return;
        }
        watchTask = scheduler.scheduleAtFixedRate(this::watchTick, period, period, unit);
    }

    /** 停止轮询（不关闭管理器，仍可手动刷新）。 */
    public synchronized void stopWatching() {
        if (watchTask != null) {
            watchTask.cancel(false);
            watchTask = null;
        }
    }

    private void watchTick() {
        try {
            Long mtime = currentMtime();
            Long previous = lastModified.get();
            if (mtime != null && !Objects.equals(mtime, previous)) {
                refresh();
            }
        } catch (RuntimeException error) {
            // 错误已通过监听器通知，这里吞掉以免杀死定时任务
        }
    }

    private Long currentMtime() {
        if (configFile == null) {
            return null;
        }
        try {
            return Files.exists(configFile) ? Files.getLastModifiedTime(configFile).toMillis() : null;
        } catch (IOException e) {
            return null;
        }
    }

    public void addListener(ConfigListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(ConfigListener listener) {
        listeners.remove(listener);
    }

    private void notifyReload(T fresh) {
        for (ConfigListener listener : listeners) {
            listener.onReload(fresh);
        }
    }

    private void notifyError(Throwable error) {
        for (ConfigListener listener : listeners) {
            listener.onError(error);
        }
    }

    @Override
    public synchronized void close() {
        stopWatching();
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }
}
