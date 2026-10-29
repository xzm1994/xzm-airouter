package com.example.airouter.service.impl;

import com.example.airouter.model.dto.chat.ChatRequest;
import com.example.airouter.model.dto.chat.ChatResponse;
import com.example.airouter.service.ChatService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ChatServiceImpl implements ChatService {
    @Resource
    private ChatModel chatModel;

    @Override
    public ChatResponse chat(ChatRequest chatRequest, Long userId, Long apiKeyId, String clientIp, String userAgent) {
        return null;
    }

    @Override
    public Flux<String> chatStream(ChatRequest chatRequest, Long userId, Long apiKeyId) {
        return null;
    }
}
