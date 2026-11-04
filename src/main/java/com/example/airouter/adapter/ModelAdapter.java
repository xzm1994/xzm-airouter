package com.example.airouter.adapter;

import com.example.airouter.model.dto.chat.ChatRequest;
import com.example.airouter.model.dto.chat.StreamChunk;
import com.example.airouter.model.entity.Model;
import com.example.airouter.model.entity.ModelProvider;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * 模型适配器接口
 */
public interface ModelAdapter {

    /**
     * 同步调用模型
     * @param model 模型实例
     * @param provider 模型提供商
     * @param chatRequest 请求
     * @return ChatResponse
     */
    ChatResponse invoke(Model model, ModelProvider provider, ChatRequest chatRequest);

    /**
     * 流式调用模型
     * @param model 模型实例
     * @param provider 模型提供商
     * @param chatRequest 请求
     * @return ChatResponse
     */
    Flux<ChatResponse> invokeStream(Model model, ModelProvider provider, ChatRequest chatRequest);

    /**
     * 流式调用模型（返回统一格式的响应快）
     * @param model 模型实例
     * @param provider 模型提供商
     * @param chatRequest 请求
     * @return ChatResponse
     */
    Flux<StreamChunk> invokeStreamChunk(Model model, ModelProvider provider, ChatRequest chatRequest);

    /**
     * 判断是否支持该提供者
     * @param providerName
     * @return
     */
    boolean supports(String providerName);
}
