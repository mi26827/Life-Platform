package com.study.lifeplatform.listener;

import cn.hutool.json.JSONUtil;
import com.study.lifeplatform.config.KafkaTopicConfig;
import com.study.lifeplatform.entity.VoucherOrder;
import com.study.lifeplatform.service.impl.SeckillVoucherServiceImpl;
import com.study.lifeplatform.service.impl.VoucherOrderServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 秒杀订单 Kafka 消费者，消费秒杀下单消息并异步落库。
 *
 * @author mi
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SeckillVoucherListener {

    @Resource
    SeckillVoucherServiceImpl seckillVoucherService;
    @Resource
    VoucherOrderServiceImpl voucherOrderService;

    /**
     * 消费秒杀订单消息：保存订单到数据库并扣减秒杀库存。
     * Kafka 为至少一次投递语义，先按订单 ID 查库判重实现幂等，重复消息直接跳过。
     *
     * @param message Kafka 消息体，内容为订单 JSON
     */
    @KafkaListener(topics = KafkaTopicConfig.SECKILL_ORDER_TOPIC, groupId = "mi-life-platform")
    public void onMessage(String message) {
        log.info("秒杀订单消费者 收到消息: {}", message);
        VoucherOrder voucherOrder = JSONUtil.toBean(message, VoucherOrder.class);
        Long orderId = voucherOrder.getId();
        VoucherOrder existOrder = voucherOrderService.getById(orderId);
        if (existOrder != null) {
            log.warn("秒杀订单重复消息 已跳过 订单ID={}", orderId);
            return;
        }
        try {
            voucherOrderService.save(voucherOrder);
        } catch (DuplicateKeyException e) {
            log.warn("秒杀订单主键冲突 已跳过 订单ID={}", orderId);
            return;
        }
        Long voucherId = voucherOrder.getVoucherId();
        seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();
        log.info("秒杀订单消费完成 订单ID={}", orderId);
    }
}