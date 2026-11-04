package com.example.airouter.service.impl;

import com.example.airouter.adapter.ModelAdapter;
import com.example.airouter.adapter.ModelAdapterFactory;
import com.example.airouter.model.dto.chat.ChatRequest;
import com.example.airouter.model.dto.chat.StreamChunk;
import com.example.airouter.model.entity.Model;
import com.example.airouter.model.entity.ModelProvider;
import com.example.airouter.service.ModelInvokeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 模型调用服务
 */
@Service
@Slf4j
public class ModelInvokeServiceImpl implements ModelInvokeService {

    @Resource
    private ModelAdapterFactory  modelAdapterFactory;

    @Override
    public ChatResponse invoke(Model model, ModelProvider modelProvider, ChatRequest chatRequest) {
        log.info("调用模型：provider={}， model={}", modelProvider.getProviderName(), model.getModelKey());
        // 根据提供者获取对应的适配器
        ModelAdapter adapter = modelAdapterFactory.getAdapter(modelProvider.getProviderName());
        // 使用适配器调用模型
        return adapter.invoke(model, modelProvider, chatRequest);
    }

    @Override
    public Flux<ChatResponse> invokeStream(Model model, ModelProvider modelProvider, ChatRequest chatRequest) {
        log.info("流式调用模型：provider={}， model={}", modelProvider.getProviderName(), model.getModelKey());
        // 根据提供者获取对应的适配器
        ModelAdapter adapter = modelAdapterFactory.getAdapter(modelProvider.getProviderName());
        // 使用适配器调用模型
        return adapter.invokeStream(model, modelProvider, chatRequest);
    }

    @Override
    public Flux<StreamChunk> invokeStreamChunk(Model model, ModelProvider modelProvider, ChatRequest chatRequest) {
        log.info("流式调用模型(统一格式)：provider={}， model={}", modelProvider.getProviderName(), model.getModelKey());
        // 根据提供者获取对应的适配器
        ModelAdapter adapter = modelAdapterFactory.getAdapter(modelProvider.getProviderName());
        // 使用适配器调用模型
        return adapter.invokeStreamChunk(model, modelProvider, chatRequest);
    }
}
