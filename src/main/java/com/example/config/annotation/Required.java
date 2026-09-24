package com.example.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记配置字段为必填：值不能为 null；String 不能为空白；
 * 数组/集合/Map 不能为空；嵌套对象的所有字段也不能为 null 引用。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Required {

    /** 可选的自定义错误说明，为空时使用默认提示。 */
    String message() default "";
}
