package com.example.config.convert;

/**
 * 单值转换器：把原始字符串转换为目标类型。
 */
@FunctionalInterface
public interface Converter<T> {

    T convert(String raw);
}
