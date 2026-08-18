package com.study.lifeplatform.ai;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.study.lifeplatform.utils.RedisConstants.AI_CHAT_MEMORY_KEY;

/**
 * 基于 Redis 的大模型会话记忆存储：将会话上下文序列化为 JSON
 * 存入 Redis，支持多轮对话与服务重启后上下文恢复。
 *
 * @author mi
 */
@Slf4j
@Component
public class RedisChatMemoryStore implements ChatMemoryStore {

    /**
     * 会话上下文保存时长（小时）
     */
    private static final long MEMORY_TTL_HOURS = 24L;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 读取指定会话的全部消息，无记录时返回空集合。
     *
     * @param memoryId 会话标识
     * @return 会话消息列表
     */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = stringRedisTemplate.opsForValue().get(AI_CHAT_MEMORY_KEY + memoryId);
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return ChatMessageDeserializer.messagesFromJson(json);
        } catch (Exception e) {
            log.error("会话上下文反序列化失败，将清空重建 会话ID={}", memoryId, e);
            stringRedisTemplate.delete(AI_CHAT_MEMORY_KEY + memoryId);
            return Collections.emptyList();
        }
    }

    /**
     * 更新指定会话的全量消息。
     *
     * @param memoryId 会话标识
     * @param messages 会话消息列表
     */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String json = ChatMessageSerializer.messagesToJson(messages);
        stringRedisTemplate.opsForValue().set(AI_CHAT_MEMORY_KEY + memoryId, json, MEMORY_TTL_HOURS, TimeUnit.HOURS);
    }

    /**
     * 删除指定会话的全部消息。
     *
     * @param memoryId 会话标识
     */
    @Override
    public void deleteMessages(Object memoryId) {
        stringRedisTemplate.delete(AI_CHAT_MEMORY_KEY + memoryId);
    }
}