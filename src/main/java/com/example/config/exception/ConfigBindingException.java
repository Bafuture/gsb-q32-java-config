package com.example.config.exception;

import java.util.Collections;
import java.util.List;

/**
 * 绑定（转换 + 校验）失败时汇总抛出，一次性包含全部问题。
 */
public class ConfigBindingException extends ConfigException {

    private final List<ConversionException> conversionErrors;
    private final List<String> violations;

    public ConfigBindingException(List<ConversionException> conversionErrors, List<String> violations) {
        super(buildMessage(conversionErrors, violations));
        this.conversionErrors = List.copyOf(conversionErrors);
        this.violations = List.copyOf(violations);
    }

    private static String buildMessage(List<ConversionException> conversionErrors, List<String> violations) {
        StringBuilder sb = new StringBuilder("配置绑定失败，共发现 ")
                .append(conversionErrors.size() + violations.size())
                .append(" 个问题:");
        for (ConversionException error : conversionErrors) {
            sb.append('\n').append("  - ").append(error.getMessage());
        }
        for (String violation : violations) {
            sb.append('\n').append("  - ").append(violation);
        }
        return sb.toString();
    }

    public List<ConversionException> getConversionErrors() {
        return Collections.unmodifiableList(conversionErrors);
    }

    public List<String> getViolations() {
        return Collections.unmodifiableList(violations);
    }
}
