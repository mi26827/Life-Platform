package com.study.lifeplatform.annotation;

/**
 * 限流维度枚举：全局、按 IP、按登录用户。
 *
 * @author mi
 */
public enum LimitType {

    /**
     * 全局限流：所有请求共享同一个窗口计数
     */
    GLOBAL,

    /**
     * 按 IP 限流：每个客户端 IP 独立窗口计数
     */
    IP,

    /**
     * 按用户限流：每个登录用户独立窗口计数
     */
    USER
}