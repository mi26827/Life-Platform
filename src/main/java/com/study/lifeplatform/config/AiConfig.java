package com.study.lifeplatform.config;

import com.study.lifeplatform.ai.AiAssistant;
import com.study.lifeplatform.ai.RedisChatMemoryStore;
import com.study.lifeplatform.ai.ShopQueryTool;
import com.study.lifeplatform.ai.ShopReservationTool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

/**
 * 智能客服配置：读取环境变量中的大模型接口配置，
 * 构建带 Redis 会话记忆与工具调用能力的 AiAssistant 代理。
 * 未配置 api-key 时降级为兜底实现，保证应用可正常启动。
 *
 * @author mi
 */
@Slf4j
@Configuration
public class AiConfig {

    /**
     * 单会话保留的最大消息条数
     */
    private static final int MAX_MEMORY_MESSAGES = 10;

    @Value("${ai.base-url:}")
    private String baseUrl;

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.model-name:gpt-4o-mini}")
    private String modelName;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ShopQueryTool shopQueryTool;

    @Resource
    private ShopReservationTool shopReservationTool;

    /**
     * 构建 AiAssistant：配置 OpenAI 兼容模型、Redis 会话记忆
     * 与商铺查询、预约两个工具；配置缺失时返回兜底实现。
     *
     * @return 智能客服对话代理
     */
    @Bean
    public AiAssistant aiAssistant() {
        if (baseUrl == null || baseUrl.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            log.warn("未配置大模型接口（ai.base-url / ai.api-key），智能客服降级为兜底实现");
            return (memoryId, message) -> "智能客服暂未启用，请配置 OPENAI_BASE_URL 与 OPENAI_API_KEY 环境变量后重启服务";
        }
        ChatLanguageModel model = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
        return AiServices.builder(AiAssistant.class)
                .chatLanguageModel(model)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(MAX_MEMORY_MESSAGES)
                        .chatMemoryStore(redisChatMemoryStore)
                        .build())
                .tools(shopQueryTool, shopReservationTool)
                .build();
    }
}