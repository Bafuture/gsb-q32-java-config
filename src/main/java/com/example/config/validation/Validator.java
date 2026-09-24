package com.example.config.validation;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

import com.example.config.annotation.Pattern;
import com.example.config.annotation.Range;
import com.example.config.annotation.Required;
import com.example.config.source.Keys;

/**
 * 基于自研注解的 Bean 校验器。
 * 递归校验嵌套对象与列表元素，所有问题收集后一次性返回（不在第一个错误处中止）。
 */
public class Validator {

    /** 校验根对象，返回全部问题描述（空列表表示通过）。 */
    public List<String> validate(Object bean) {
        List<String> violations = new ArrayList<>();
        if (bean == null) {
            return violations;
        }
        validateBean(bean, "", violations, java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
        return violations;
    }

    private void validateBean(Object bean, String prefix, List<String> violations, Set<Object> seen) {
        if (bean == null || !seen.add(bean)) {
            return;
        }
        for (Field field : allFields(bean.getClass())) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            Object value;
            try {
                value = field.get(bean);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("无法访问字段: " + field, e);
            }
            String key = Keys.join(prefix, Keys.camelToKebab(field.getName()));

            validateField(field, value, key, violations, seen);
        }
    }

    private void validateField(Field field, Object value, String key, List<String> violations, Set<Object> seen) {
        Required required = field.getAnnotation(Required.class);
        if (required != null && isEmpty(value)) {
            violations.add(required.message().isEmpty()
                    ? "校验失败: 配置键=" + key + ", 原因为必填项但值缺失"
                    : "校验失败: 配置键=" + key + ", 原因=" + required.message());
        }

        if (value != null) {
            Range range = field.getAnnotation(Range.class);
            if (range != null && value instanceof Number number) {
                checkRange(key, number.doubleValue(), range, violations);
            }

            Pattern pattern = field.getAnnotation(Pattern.class);
            if (pattern != null) {
                java.util.regex.Pattern compiled = compilePattern(key, pattern, violations);
                if (compiled != null) {
                    if (value instanceof String text) {
                        checkPattern(key, text, compiled, pattern, violations);
                    } else if (value instanceof Collection<?> collection) {
                        int index = 0;
                        for (Object element : collection) {
                            if (element instanceof String text) {
                                checkPattern(key + "[" + index + "]", text, compiled, pattern, violations);
                            }
                            index++;
                        }
                    }
                }
            }
        }

        if (value != null) {
            if (isNestedBean(value.getClass())) {
                validateBean(value, key, violations, seen);
            } else if (value instanceof Collection<?> collection) {
                int index = 0;
                for (Object element : collection) {
                    if (element != null && isNestedBean(element.getClass())) {
                        validateBean(element, key + "[" + index + "]", violations, seen);
                    }
                    index++;
                }
            }
        }
    }

    private void checkRange(String key, double number, Range range, List<String> violations) {
        if (number < range.min() || number > range.max()) {
            String message = range.message();
            violations.add(message.isEmpty()
                    ? "校验失败: 配置键=" + key + ", 原因值 " + number + " 超出允许范围 [" + range.min() + ", " + range.max() + "]"
                    : "校验失败: 配置键=" + key + ", 原因=" + message);
        }
    }

    private java.util.regex.Pattern compilePattern(String key, Pattern pattern, List<String> violations) {
        try {
            return java.util.regex.Pattern.compile(pattern.regexp());
        } catch (PatternSyntaxException e) {
            violations.add("校验失败: 配置键=" + key + ", 原因注解上的正则表达式非法: " + e.getDescription());
            return null;
        }
    }

    private void checkPattern(String key, String text, java.util.regex.Pattern compiled,
                              Pattern pattern, List<String> violations) {
        if (!compiled.matcher(text).matches()) {
            String message = pattern.message();
            violations.add(message.isEmpty()
                    ? "校验失败: 配置键=" + key + ", 原因值 '" + text + "' 不匹配正则 " + pattern.regexp()
                    : "校验失败: 配置键=" + key + ", 原因=" + message);
        }
    }

    private boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (value.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(value) == 0;
        }
        return false;
    }

    private boolean isNestedBean(Class<?> type) {
        if (type.isPrimitive() || type.isEnum() || type.isArray()) {
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
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    fields.add(field);
                }
            }
            current = current.getSuperclass();
        }
        return fields;
    }

}
