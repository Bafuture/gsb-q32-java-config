package com.example.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 正则表达式校验，仅对 String（或 List&lt;String&gt; 的每个元素）生效。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Pattern {

    /** 必须匹配的正则表达式（matches 语义，需要匹配整串）。 */
    String regexp();

    String message() default "";
}
