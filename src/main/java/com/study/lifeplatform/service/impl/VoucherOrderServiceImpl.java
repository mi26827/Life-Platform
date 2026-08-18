package com.study.lifeplatform.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.study.lifeplatform.dto.Result;
import com.study.lifeplatform.entity.VoucherOrder;
import com.study.lifeplatform.mapper.VoucherOrderMapper;
import com.study.lifeplatform.service.ISeckillVoucherService;
import com.study.lifeplatform.service.IVoucherOrderService;
import com.study.lifeplatform.config.KafkaTopicConfig;
import com.study.lifeplatform.utils.RedisIdWorker;
import com.study.lifeplatform.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;

/**
 * 优惠券订单服务实现类，秒杀资格由 Lua 脚本判定，
 * 订单落库由 Kafka 异步消费完成。
 *
 * @author mi
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    /**
     * 订单状态：未支付
     */
    private static final int ORDER_STATUS_UNPAID = 1;

    /**
     * 订单状态：已支付
     */
    private static final int ORDER_STATUS_PAID = 2;

    /**
     * 订单状态：已取消（关闭）
     */
    private static final int ORDER_STATUS_CLOSED = 4;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 秒杀资格判定脚本
     */
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 秒杀下单入口：Lua 脚本原子校验库存与一人一单，
     * 校验通过后将订单消息发送到 Kafka 异步落库。
     *
     * @param voucherId 优惠券 id
     * @return 含订单 id 的结果
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = 0;
        if (result != null) {
            r = result.intValue();
        }
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        VoucherOrder order = new VoucherOrder();
        order.setId(orderId);
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        order.setStatus(ORDER_STATUS_UNPAID);
        String jsonStr = JSONUtil.toJsonStr(order);
        try {
            kafkaTemplate.send(KafkaTopicConfig.SECKILL_ORDER_TOPIC, String.valueOf(orderId), jsonStr);
        } catch (Exception e) {
            log.error("发送 Kafka 消息失败，订单ID: {}", orderId, e);
            throw new RuntimeException("发送消息失败");
        }
        return Result.ok(orderId);
    }

    /**
     * 创建订单：一人一单校验、扣减库存并保存订单。
     *
     * @param voucherOrder 订单信息
     */
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            log.error("用户已经购买过一次了");
            return;
        }
        boolean success = seckillVoucherService
                .update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!success) {
            log.error("库存不足");
            return;
        }
        save(voucherOrder);
    }

    /**
     * 支付订单：以 status=1（未支付）为条件的乐观锁 CAS 更新，
     * 与定时关单并发时仅一方成功，避免已关闭订单被支付。
     *
     * @param orderId 订单 id
     * @return 支付结果
     */
    @Override
    public Result payOrder(Long orderId) {
        boolean success = update()
                .eq("id", orderId)
                .eq("status", ORDER_STATUS_UNPAID)
                .set("status", ORDER_STATUS_PAID)
                .set("pay_time", LocalDateTime.now())
                .set("update_time", LocalDateTime.now())
                .update();
        if (!success) {
            log.warn("订单支付失败，订单状态已变更 订单ID={}", orderId);
            return Result.fail("订单状态已变更，支付失败");
        }
        return Result.ok();
    }

    /**
     * 关闭订单：以 status=1（未支付）为条件的乐观锁 CAS 更新，
     * 与支付并发冲突时更新失败并返回错误。
     *
     * @param orderId 订单 id
     * @return 关单结果
     */
    @Override
    public Result closeOrder(Long orderId) {
        boolean success = update()
                .eq("id", orderId)
                .eq("status", ORDER_STATUS_UNPAID)
                .set("status", ORDER_STATUS_CLOSED)
                .set("update_time", LocalDateTime.now())
                .update();
        if (!success) {
            log.warn("订单关闭失败，订单状态已变更 订单ID={}", orderId);
            return Result.fail("订单状态已变更，关闭失败");
        }
        return Result.ok();
    }
}