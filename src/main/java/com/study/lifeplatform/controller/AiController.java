package com.study.lifeplatform.controller;

import com.study.lifeplatform.ai.AiAssistant;
import com.study.lifeplatform.dto.Result;
import com.study.lifeplatform.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 智能客服接口：基于 LangChain4j 的多轮对话，
 * 以登录用户 id 隔离会话上下文，支持工具调用。
 *
 * @author mi
 */
@Slf4j
@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private AiAssistant aiAssistant;

    /**
     * 智能客服对话：以当前登录用户 id 作为会话标识，
     * 回答内容由系统提示词约束，支持商铺查询与预约工具调用。
     *
     * @param params 请求参数，包含 message 字段
     * @return 客服回答文本
     */
    @PostMapping("/chat")
    public Result chat(@RequestBody Map<String, String> params) {
        String message = params.get("message");
        if (message == null || message.trim().isEmpty()) {
            return Result.fail("消息不能为空");
        }
        Long userId = UserHolder.getUser().getId();
        log.info("智能客服对话 用户ID={} 消息={}", userId, message);
        try {
            String answer = aiAssistant.chat(userId.toString(), message);
            return Result.ok(answer);
        } catch (Exception e) {
            log.error("智能客服对话异常 用户ID={}", userId, e);
            return Result.fail("智能客服暂时无法回复，请稍后再试");
        }
    }
}