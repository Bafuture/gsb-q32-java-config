package com.example.config.core;

/**
 * 配置动态刷新监听器。两个回调都有默认空实现，按需覆写。
 */
public interface ConfigListener {

    /** 重新加载成功后回调，参数为新的配置对象。 */
    default void onReload(Object newConfig) {
    }

    /** 重新加载失败时回调，旧配置保持不变。 */
    default void onError(Throwable error) {
    }
}
