package com.study.lifeplatform.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 智能客服对话接口：由 LangChain4j AiServices 动态代理实现，
 * 以用户 id 作为会话标识支持多轮对话，并可自动调用工具完成查询与预约。
 *
 * @author mi
 */
public interface AiAssistant {

    /**
     * 多轮对话：系统提示词约束回答范围，会话上下文按 memoryId 隔离。
     *
     * @param memoryId 会话标识（用户 id）
     * @param message 用户消息
     * @return 客服回答
     */
    @SystemMessage("你是本地生活服务平台「MI生活」的智能客服助手。"
            + "只回答与平台相关的问题，包括商铺查询、优惠券、秒杀、订单等。"
            + "用户询问商铺信息时调用查询工具，表达预约意向时调用预约工具。"
            + "与平台无关的问题请礼貌拒绝，回答保持简洁友好。")
    String chat(@MemoryId String memoryId, @UserMessage String message);
}