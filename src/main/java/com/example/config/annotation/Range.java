package com.example.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数值范围校验，适用于所有数值类型（含其包装类型）。边界为闭区间。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Range {

    double min() default -Double.MAX_VALUE;

    double max() default Double.MAX_VALUE;

    String message() default "";
}
