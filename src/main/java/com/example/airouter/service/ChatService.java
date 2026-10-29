package com.example.airouter.service;

import com.example.airouter.model.dto.chat.ChatRequest;
import com.example.airouter.model.dto.chat.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * 对话服务
 */
public interface ChatService {
    /**
     * 非流式聊天
     *
     * @param chatRequest 聊天请求
     * @param userId      用户ID
     * @param apiKeyId    API Key ID
     * @param clientIp    客户端IP
     * @param userAgent   User-Agent
     * @return 聊天响应
     */
    ChatResponse chat(ChatRequest chatRequest, Long userId, Long apiKeyId, String clientIp, String userAgent);

    /**
     * 流式聊天
     * @param chatRequest 聊天请求
     * @param userId 用户ID
     * @param apiKeyId API Key ID
     * @return 聊天响应
     */
    Flux<String> chatStream(ChatRequest chatRequest, Long userId, Long apiKeyId);
}
