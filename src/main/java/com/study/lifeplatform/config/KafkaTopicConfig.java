package com.study.lifeplatform.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka 主题配置，声明秒杀订单异步落库所需的主题。
 *
 * @author mi
 */
@Configuration
public class KafkaTopicConfig {

    /**
     * 秒杀订单主题名称
     */
    public static final String SECKILL_ORDER_TOPIC = "seckill.order";

    /**
     * 秒杀订单死信主题名称，消费重试仍失败的消息进入该主题等待人工处理
     */
    public static final String SECKILL_ORDER_DLT_TOPIC = "seckill.order.dlt";

    /**
     * 声明秒杀订单主题，3 分区 1 副本，保证并发消费能力。
     *
     * @return 主题定义
     */
    @Bean
    public NewTopic seckillOrderTopic() {
        return new NewTopic(SECKILL_ORDER_TOPIC, 3, (short) 1);
    }

    /**
     * 声明秒杀订单死信主题，3 分区 1 副本，与业务主题分区数保持一致。
     *
     * @return 死信主题定义
     */
    @Bean
    public NewTopic seckillOrderDltTopic() {
        return new NewTopic(SECKILL_ORDER_DLT_TOPIC, 3, (short) 1);
    }
}