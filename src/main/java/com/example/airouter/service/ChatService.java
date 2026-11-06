package com.example.airouter.service;

import com.example.airouter.model.dto.chat.ChatRequest;
import com.example.airouter.model.dto.chat.ChatResponse;
import com.example.airouter.model.dto.chat.StreamResponse;
import reactor.core.publisher.Flux;

/**
 * 对话服务
 */
public interface ChatService {
//    /**
//     * 非流式聊天
//     *
//     * @param chatRequest 聊天请求
//     * @param userId      用户ID
//     * @param apiKeyId    API Key ID
//     * @return 聊天响应
//     */
//    ChatResponse chat(ChatRequest chatRequest, Long userId, Long apiKeyId);


    /**
     * 飞流式聊天
     * @param chatRequest
     * @param userId
     * @param apiKeyId
     * @param clientIp
     * @param userAgent
     * @return
     */
    ChatResponse chat(ChatRequest chatRequest, Long userId, Long apiKeyId, String clientIp, String userAgent);

    /**
     * 流式聊天（返回 OpenAI SSE 格式）
     *
     * @param chatRequest 聊天请求
     * @param userId      用户ID
     * @param apiKeyId    API Key ID
     * @param clientIp    客户端IP
     * @param userAgent   User-Agent
     * @return 流式聊天响应
     */
    Flux<StreamResponse> chatStream(ChatRequest chatRequest, Long userId, Long apiKeyId, String clientIp, String userAgent);

//    /**
//     * 流式聊天
//     * @param chatRequest 聊天请求
//     * @param userId 用户ID
//     * @param apiKeyId API Key ID
//     * @return 聊天响应
//     */
//    Flux<String> chatStream(ChatRequest chatRequest, Long userId, Long apiKeyId);
}
