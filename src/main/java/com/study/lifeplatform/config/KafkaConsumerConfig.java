package com.study.lifeplatform.config;

import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.SeekToCurrentErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka 消费者容器配置，定义消费失败重试与死信投递策略。
 * 消费失败后按固定间隔重试，重试耗尽仍未成功的消息投递到死信主题 seckill.order.dlt，等待人工介入。
 *
 * @author mi
 */
@Configuration
public class KafkaConsumerConfig {

    /**
     * 重试间隔毫秒数
     */
    private static final long RETRY_INTERVAL_MS = 1000L;

    /**
     * 最大重试次数
     */
    private static final long MAX_RETRY_COUNT = 3L;

    /**
     * 构造消费错误处理器：重试 3 次、间隔 1 秒，仍失败则投递死信主题。
     *
     * @param kafkaTemplate Kafka 发送模板，用于死信投递
     * @return 消费错误处理器
     */
    @Bean
    public SeekToCurrentErrorHandler seekToCurrentErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        return new SeekToCurrentErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRY_COUNT));
    }

    /**
     * 覆盖默认监听容器工厂，注入重试与死信错误处理器。
     *
     * @param configurer Spring Boot 自动配置的容器工厂配置器
     * @param consumerFactory 消费者工厂
     * @param errorHandler 消费错误处理器
     * @return 自定义监听容器工厂
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            SeekToCurrentErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.setErrorHandler(errorHandler);
        return factory;
    }
}