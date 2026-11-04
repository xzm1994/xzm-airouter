package com.example.airouter.service;

import com.example.airouter.model.dto.chat.ChatRequest;
import com.example.airouter.model.dto.chat.StreamChunk;
import com.example.airouter.model.entity.Model;
import com.example.airouter.model.entity.ModelProvider;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * 模型调用服务
 */
public interface ModelInvokeService {
    ChatResponse invoke(Model model, ModelProvider modelProvider, ChatRequest  chatRequest);

    Flux<ChatResponse> invokeStream(Model model, ModelProvider modelProvider, ChatRequest  chatRequest);

    Flux<StreamChunk> invokeStreamChunk(Model model, ModelProvider modelProvider, ChatRequest  chatRequest);
}
