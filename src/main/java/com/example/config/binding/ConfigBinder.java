package com.example.config.binding;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

import com.example.config.convert.TypeConverter;
import com.example.config.exception.ConfigBindingException;
import com.example.config.exception.ConversionException;
import com.example.config.source.ConfigSource;
import com.example.config.source.Keys;
import com.example.config.validation.Validator;

/**
 * 把多个 {@link ConfigSource} 的扁平字符串配置绑定到嵌套配置类。
 *
 * <p>字段名到配置键的规则（详见 README）：</p>
 * <ul>
 *   <li>字段 camelCase -&gt; kebab-case：{@code serverPort} -&gt; {@code server-port}；</li>
 *   <li>嵌套对象用 {@code .} 连接：{@code server.port}；</li>
 *   <li>对象列表用下标：{@code servers[0].host}；</li>
 *   <li>标量列表（{@code List&lt;Integer&gt;} 等）支持 {@code ports=8080,8081} 逗号写法，
 *       也支持 {@code ports[0]=8080} 下标写法；下标写法存在时下标条目优先，二者合并（按下标）。</li>
 * </ul>
 *
 * <p>未出现在任何来源中的字段保留 Java 默认值/字段初始化值（即「代码内默认值」）。
 * 绑定完成后执行 {@link Validator}，转换错误与校验错误一次性汇总抛出。</p>
 */
public class ConfigBinder {

    private final List<ConfigSource> sources;
    private final TypeConverter converter;
    private final Validator validator;

    public ConfigBinder(List<ConfigSource> sources, TypeConverter converter) {
        this(sources, converter, new Validator());
    }

    public ConfigBinder(List<ConfigSource> sources, TypeConverter converter, Validator validator) {
        this.sources = List.copyOf(sources);
        this.converter = converter;
        this.validator = validator;
    }

    public <T> T bind(Class<T> type) {
        T bean = type.cast(newInstance(type, ""));
        List<ConversionException> errors = new ArrayList<>();
        populate(bean, "", errors);
        List<String> violations = validator.validate(bean);
        if (!errors.isEmpty() || !violations.isEmpty()) {
            throw new ConfigBindingException(errors, violations);
        }
        return bean;
    }

    private void populate(Object bean, String prefix, List<ConversionException> errors) {
        for (Field field : allFields(bean.getClass())) {
            field.setAccessible(true);
            String key = Keys.join(prefix, Keys.camelToKebab(field.getName()));
            Object existingValue = getFieldValue(bean, field);

            Type fieldType = field.getGenericType();
            Optional<Type> elementType = listElementType(fieldType);

            try {
                if (elementType.isPresent()) {
                    bindListField(bean, field, key, elementType.get(), errors);
                } else if (isNestedBean(field.getType())) {
                    Object nested = existingValue != null ? existingValue : newInstance(field.getType(), key);
                    populate(nested, key, errors);
                    setFieldValue(bean, field, nested);
                } else {
                    findValue(key).ifPresent(raw -> {
                        Object converted = converter.convert(key, raw, fieldType);
                        setFieldValue(bean, field, converted);
                    });
                }
            } catch (ConversionException e) {
                errors.add(e);
            }
        }
    }

    private void bindListField(Object bean, Field field, String key, Type elementType,
                               List<ConversionException> errors) {
        // 1) 下标写法（含对象列表 servers[0].host 与标量列表 ports[0]）
        TreeSet<Integer> indices = collectIndices(key);
        if (!indices.isEmpty()) {
            List<Object> list = new ArrayList<>();
            Class<?> elementClass = erase(elementType);
            for (int index : indices) {
                String indexedKey = key + "[" + index + "]";
                if (isNestedBean(elementClass)) {
                    Object element = newInstance(elementClass, indexedKey);
                    populate(element, indexedKey, errors);
                    list.add(element);
                } else {
                    String raw = findValue(indexedKey).orElse(null);
                    if (raw != null) {
                        list.add(converter.convert(indexedKey, raw, elementType));
                    } else {
                        // 下标存在（可能仅有子键），元素类型为标量但没有直接值：跳过
                    }
                }
            }
            setFieldValue(bean, field, list);
            return;
        }

        // 2) 逗号分隔写法（仅标量列表）
        Optional<String> raw = findValue(key);
        if (raw.isPresent() && !isNestedBean(erase(elementType))) {
            setFieldValue(bean, field, converter.convertList(key, raw.get(), elementType));
        }
    }

    /** 从所有来源的已知键中发现 key[index] 或 key[index]. 形式的下标集合。 */
    private TreeSet<Integer> collectIndices(String prefix) {
        TreeSet<Integer> indices = new TreeSet<>();
        String directPrefix = prefix + "[";
        for (ConfigSource source : sources) {
            for (String candidate : source.entries().keySet()) {
                if (candidate.startsWith(directPrefix)) {
                    int close = candidate.indexOf(']', directPrefix.length());
                    if (close > directPrefix.length()) {
                        String number = candidate.substring(directPrefix.length(), close);
                        try {
                            indices.add(Integer.parseInt(number));
                        } catch (NumberFormatException ignored) {
                            // 非法下标忽略
                        }
                    }
                }
            }
        }
        return indices;
    }

    private Optional<String> findValue(String key) {
        // 来源按优先级低 -&gt; 高排列；高优先级覆盖低优先级，因此反向取第一个命中
        for (int i = sources.size() - 1; i >= 0; i--) {
            ConfigSource source = sources.get(i);
            Optional<String> value = source.get(key);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private Object getFieldValue(Object bean, Field field) {
        try {
            return field.get(bean);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("无法读取字段: " + field, e);
        }
    }

    private void setFieldValue(Object bean, Field field, Object value) {
        try {
            field.set(bean, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("无法写入字段: " + field, e);
        }
    }

    private Object newInstance(Class<?> type, String key) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("配置类 " + type.getName()
                    + " 缺少无参构造方法（配置键=" + key + "）", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法实例化配置类 " + type.getName()
                    + "（配置键=" + key + "）: " + e.getMessage(), e);
        }
    }

    private Optional<Type> listElementType(Type type) {
        if (type instanceof ParameterizedType parameterized
                && List.class.isAssignableFrom((Class<?>) parameterized.getRawType())) {
            return Optional.of(parameterized.getActualTypeArguments()[0]);
        }
        return Optional.empty();
    }

    private Class<?> erase(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterized) {
            return (Class<?>) parameterized.getRawType();
        }
        throw new IllegalArgumentException("不支持的泛型类型: " + type);
    }

    private boolean isNestedBean(Class<?> type) {
        if (type.isPrimitive() || type.isEnum() || type.isArray() || type == String.class) {
            return false;
        }
        if (Number.class.isAssignableFrom(type) || type == Boolean.class || type == Character.class) {
            return false;
        }
        if (java.time.temporal.Temporal.class.isAssignableFrom(type)
                || java.nio.file.Path.class.isAssignableFrom(type)) {
            return false;
        }
        String name = type.getName();
        return !name.startsWith("java.") && !name.startsWith("javax.") && !name.startsWith("sun.");
    }

    private List<Field> allFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (!Modifier.isStatic(modifiers) && !field.isSynthetic()) {
                    fields.add(field);
                }
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    public List<ConfigSource> sources() {
        return sources;
    }

    public TypeConverter converter() {
        return converter;
    }
}
