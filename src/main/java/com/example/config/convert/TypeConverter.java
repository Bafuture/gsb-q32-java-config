package com.example.config.convert;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.config.exception.ConversionException;

/**
 * 类型转换注册表。内置支持：
 *
 * <ul>
 *   <li>{@code String}、{@code char/Character}；</li>
 *   <li>{@code boolean/Boolean}（true|false|1|0|yes|no|on|off，忽略大小写）；</li>
 *   <li>{@code byte/short/int/long/float/double} 及其包装类型；</li>
 *   <li>任意 {@code enum}（按枚举常量名匹配，忽略大小写）；</li>
 *   <li>{@link Duration}：简洁写法 {@code 30s}、{@code 5m}、{@code 2h}、{@code 500ms}、{@code 1d}，
 *       也接受 ISO-8601 的 {@code PT30S}；</li>
 *   <li>{@link Path}：文件路径；</li>
 *   <li>{@code List<T>}：逗号分隔的标量列表（{@code a, b ,c}，元素去空白，空元素忽略），
 *       元素类型递归使用本注册表转换。</li>
 * </ul>
 */
public class TypeConverter {

    private final Map<Class<?>, Converter<?>> converters = new LinkedHashMap<>();

    public TypeConverter() {
        registerDefaults();
    }

    private void registerDefaults() {
        converters.put(String.class, raw -> raw);
        converters.put(Character.class, raw -> {
            if (raw.length() != 1) {
                throw new IllegalArgumentException("期望单个字符，实际长度=" + raw.length());
            }
            return raw.charAt(0);
        });
        converters.put(Boolean.class, raw -> {
            switch (raw.trim().toLowerCase()) {
                case "true": case "1": case "yes": case "on": return Boolean.TRUE;
                case "false": case "0": case "no": case "off": return Boolean.FALSE;
                default: throw new IllegalArgumentException("无法识别的布尔值: " + raw);
            }
        });
        converters.put(Byte.class, raw -> Byte.valueOf(raw.trim()));
        converters.put(Short.class, raw -> Short.valueOf(raw.trim()));
        converters.put(Integer.class, raw -> Integer.valueOf(raw.trim()));
        converters.put(Long.class, raw -> Long.valueOf(raw.trim()));
        converters.put(Float.class, raw -> Float.valueOf(raw.trim()));
        converters.put(Double.class, raw -> Double.valueOf(raw.trim()));
        converters.put(Duration.class, DurationStyle::parse);
        converters.put(Path.class, raw -> {
            try {
                return Paths.get(raw);
            } catch (InvalidPathException e) {
                throw new IllegalArgumentException("非法文件路径: " + e.getReason());
            }
        });
    }

    public <T> void register(Class<T> type, Converter<T> converter) {
        converters.put(type, converter);
    }

    public boolean supports(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz.isEnum() || converters.containsKey(clazz) || wrappers(clazz) != null;
        }
        if (type instanceof ParameterizedType parameterized
                && parameterized.getRawType() == List.class) {
            return supports(parameterized.getActualTypeArguments()[0]);
        }
        return false;
    }

    private Class<?> wrappers(Class<?> primitive) {
        if (!primitive.isPrimitive()) {
            return converters.containsKey(primitive) ? primitive : null;
        }
        if (primitive == boolean.class) return Boolean.class;
        if (primitive == char.class) return Character.class;
        if (primitive == byte.class) return Byte.class;
        if (primitive == short.class) return Short.class;
        if (primitive == int.class) return Integer.class;
        if (primitive == long.class) return Long.class;
        if (primitive == float.class) return Float.class;
        if (primitive == double.class) return Double.class;
        return null;
    }

    /** 转换单个标量值，失败时抛出包含「键 + 期望类型 + 原始值」的 {@link ConversionException}。 */
    @SuppressWarnings("unchecked")
    public <T> T convert(String key, String raw, Type type) {
        Class<?> rawClass = erase(type);
        Class<?> target = rawClass != null && rawClass.isPrimitive() ? wrappers(rawClass) : rawClass;
        String expectedType = describeType(type);
        try {
            if (rawClass != null && rawClass.isEnum()) {
                return (T) convertEnum(key, raw, rawClass);
            }
            Converter<?> converter = target == null ? null : converters.get(target);
            if (converter == null) {
                throw new IllegalArgumentException("不支持的目标类型");
            }
            return (T) converter.convert(raw);
        } catch (ConversionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ConversionException(key, expectedType, raw, e.getMessage(), e);
        }
    }

    /** 转换逗号分隔列表，单个元素失败同样给出带列表元素键的明确错误。 */
    public List<Object> convertList(String key, String raw, Type elementType) {
        String[] parts = raw.split(",", -1);
        java.util.List<Object> result = new java.util.ArrayList<>(parts.length);
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            result.add(convert(key, trimmed, elementType));
        }
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object convertEnum(String key, String raw, Class<?> enumType) {
        Object[] constants = enumType.getEnumConstants();
        for (Object constant : constants) {
            if (((Enum<?>) constant).name().equalsIgnoreCase(raw.trim())) {
                return constant;
            }
        }
        throw new ConversionException(key, describeType(enumType), raw,
                "不是合法的枚举常量，可选值: " + allowedEnumValues(enumType));
    }

    private String allowedEnumValues(Class<?> enumType) {
        Object[] constants = enumType.getEnumConstants();
        String[] names = new String[constants.length];
        for (int i = 0; i < constants.length; i++) {
            names[i] = ((Enum<?>) constants[i]).name();
        }
        return String.join(", ", names);
    }

    private Class<?> erase(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterized) {
            return (Class<?>) parameterized.getRawType();
        }
        return null;
    }

    /** 期望类型的可读名称，用于错误信息。 */
    public static String describeType(Type type) {
        if (type instanceof Class<?> clazz) {
            if (clazz.isEnum()) {
                return "枚举 " + clazz.getSimpleName();
            }
            return clazz.getSimpleName();
        }
        if (type instanceof ParameterizedType parameterized) {
            StringBuilder sb = new StringBuilder(((Class<?>) parameterized.getRawType()).getSimpleName()).append('<');
            Type[] args = parameterized.getActualTypeArguments();
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(describeType(args[i]));
            }
            return sb.append('>').toString();
        }
        return String.valueOf(type);
    }

    public static Optional<Type> listElementType(Type type) {
        if (type instanceof ParameterizedType parameterized
                && parameterized.getRawType() == List.class) {
            return Optional.of(parameterized.getActualTypeArguments()[0]);
        }
        return Optional.empty();
    }

    public Map<Class<?>, Converter<?>> registeredConverters() {
        return Collections.unmodifiableMap(converters);
    }
}
