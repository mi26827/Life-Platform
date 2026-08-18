package com.study.lifeplatform.aspect;

import cn.hutool.core.util.StrUtil;
import com.study.lifeplatform.annotation.LimitType;
import com.study.lifeplatform.annotation.RateLimit;
import com.study.lifeplatform.dto.Result;
import com.study.lifeplatform.dto.UserDTO;
import com.study.lifeplatform.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.study.lifeplatform.utils.RedisConstants.RATE_LIMIT_KEY;

/**
 * 滑动窗口限流切面：拦截标注 {@link RateLimit} 的接口方法，
 * 使用 Redis ZSet 记录窗口内请求时间戳实现滑动窗口计数，
 * 超出阈值时直接返回失败结果，保护接口不被突发流量打垮。
 *
 * @author mi
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 环绕通知：执行滑动窗口限流判定后再放行原方法。
     *
     * @param joinPoint 切点
     * @param rateLimit 限流注解配置
     * @return 原方法返回值或限流失败结果
     * @throws Throwable 原方法抛出的异常
     */
    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String key = buildLimitKey(method, rateLimit.limitType());
        if (tryAcquire(key, rateLimit.window(), rateLimit.maxCount())) {
            return joinPoint.proceed();
        }
        log.warn("接口触发限流 方法={} 限流键={}", method.getName(), key);
        Class<?> returnType = method.getReturnType();
        if (Result.class.isAssignableFrom(returnType)) {
            return Result.fail("请求过于频繁，请稍后再试");
        }
        throw new RuntimeException("请求过于频繁，请稍后再试");
    }

    /**
     * 滑动窗口获取许可：清理窗口外记录后统计窗口内请求数，
     * 未超阈值则记录本次请求并放行。
     *
     * @param key 限流缓存键
     * @param window 窗口时长（秒）
     * @param maxCount 窗口内最大请求次数
     * @return 是否获取到许可
     */
    private boolean tryAcquire(String key, int window, int maxCount) {
        long now = System.currentTimeMillis();
        long windowStart = now - window * 1000L;
        stringRedisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
        Long count = stringRedisTemplate.opsForZSet().zCard(key);
        if (count != null && count >= maxCount) {
            return false;
        }
        stringRedisTemplate.opsForZSet().add(key, now + "-" + UUID.randomUUID(), now);
        stringRedisTemplate.expire(key, window + 1, TimeUnit.SECONDS);
        return true;
    }

    /**
     * 构建限流键：方法签名作为基础键，按维度追加全局、IP 或用户标识。
     *
     * @param method 目标方法
     * @param limitType 限流维度
     * @return 限流缓存键
     */
    private String buildLimitKey(Method method, LimitType limitType) {
        String base = RATE_LIMIT_KEY + method.getDeclaringClass().getSimpleName() + ":" + method.getName();
        if (limitType == LimitType.IP) {
            return base + ":" + getClientIp();
        }
        if (limitType == LimitType.USER) {
            UserDTO user = UserHolder.getUser();
            if (user != null && user.getId() != null) {
                return base + ":u:" + user.getId();
            }
            return base + ":ip:" + getClientIp();
        }
        return base;
    }

    /**
     * 获取客户端真实 IP：优先取代理透传头，否则取远端地址。
     *
     * @return 客户端 IP
     */
    private String getClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
            int index = ip.indexOf(',');
            return index > 0 ? ip.substring(0, index) : ip;
        }
        ip = request.getRemoteAddr();
        return StrUtil.isBlank(ip) ? "unknown" : ip;
    }
}