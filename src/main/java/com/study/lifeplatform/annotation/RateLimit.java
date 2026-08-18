package com.study.lifeplatform.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解：基于 Redis ZSet 滑动窗口统计，
 * 支持全局、IP、用户三种限流维度。
 *
 * @author mi
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限流维度：全局、IP、用户
     */
    LimitType limitType() default LimitType.GLOBAL;

    /**
     * 滑动窗口时长（秒）
     */
    int window() default 1;

    /**
     * 窗口内允许的最大请求次数
     */
    int maxCount() default 10;
}