package com.study.lifeplatform.ai;

import com.study.lifeplatform.entity.Shop;
import com.study.lifeplatform.service.IShopService;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static com.study.lifeplatform.utils.RedisConstants.AI_RESERVATION_KEY;

/**
 * 商铺预约工具：智能客服 Function Calling 能力，
 * 大模型识别用户预约意图后自动调用，将预约记录写入 Redis。
 *
 * @author mi
 */
@Slf4j
@Component
public class ShopReservationTool {

    /**
     * 预约记录保存时长（小时）
     */
    private static final long RESERVATION_TTL_HOURS = 48L;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Resource
    private IShopService shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 为指定用户预约指定商铺，预约记录写入 Redis 并设置过期时间。
     *
     * @param shopId 商铺 id
     * @param userId 用户 id
     * @return 预约结果描述，供大模型组织回答
     */
    @Tool("为用户预约指定商铺，需要提供商铺ID和用户ID")
    public String reserveShop(Long shopId, Long userId) {
        log.info("智能客服预约商铺 商铺ID={} 用户ID={}", shopId, userId);
        Shop shop = shopService.getById(shopId);
        if (shop == null) {
            return "预约失败：商铺不存在";
        }
        String reservation = String.format("预约商铺：%s，预约人ID：%s，预约时间：%s",
                shop.getName(), userId, LocalDateTime.now().format(FORMATTER));
        stringRedisTemplate.opsForList().leftPush(AI_RESERVATION_KEY + userId, reservation);
        stringRedisTemplate.expire(AI_RESERVATION_KEY + userId, RESERVATION_TTL_HOURS, java.util.concurrent.TimeUnit.HOURS);
        return "预约成功：" + reservation;
    }
}