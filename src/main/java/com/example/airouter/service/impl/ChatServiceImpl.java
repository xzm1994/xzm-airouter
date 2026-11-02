package com.example.airouter.service.impl;

import cn.hutool.core.util.IdUtil;
import com.example.airouter.exception.BusinessException;
import com.example.airouter.exception.ErrorCode;
import com.example.airouter.model.dto.chat.ChatMessage;
import com.example.airouter.model.dto.chat.ChatRequest;
import com.example.airouter.model.dto.chat.ChatResponse;
import com.example.airouter.service.ChatService;
import com.example.airouter.service.RequestLogService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ChatServiceImpl implements ChatService {
    @Resource
    private ChatModel chatModel;

    @Resource
    private RequestLogService requestLogService;

    @Override
    public ChatResponse chat(ChatRequest chatRequest, Long userId, Long apiKeyId) {
        long startTime = System.currentTimeMillis();
        String modelName = chatRequest.getModel();

        // 构筑prompt
        Prompt prompt = buildPrompt(chatRequest);

        // 调用AI模型
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        org.springframework.ai.chat.model.ChatResponse aiResponse = chatClient.prompt(prompt).call().chatResponse();
        // 转换响应格式
        assert aiResponse != null;
        ChatResponse response = convertResponse(aiResponse, modelName);

        // 记录请求日志
        long duration = System.currentTimeMillis() - startTime;
        requestLogService.logRequest(userId, apiKeyId, modelName, response.getUsage().getPromptTokens(),
                response.getUsage().getCompletionTokens(), response.getUsage().getTotalTokens(), (int) duration, "success", null);
        return response;
    }

    /**
     * 转化响应格式
     * @param aiResponse ai返回的响应
     * @param modelName 模型名称
     * @return ChatResponse
     */
    private ChatResponse convertResponse(org.springframework.ai.chat.model.ChatResponse aiResponse, String modelName) {
        String context = aiResponse.getResult().getOutput().getText();
        ChatResponse.Usage usage = ChatResponse.Usage.builder().promptTokens(aiResponse.getMetadata().getUsage().getPromptTokens() != null ? aiResponse.getMetadata().getUsage().getPromptTokens() : 0)
                .completionTokens(aiResponse.getMetadata().getUsage().getCompletionTokens() != null ? aiResponse.getMetadata().getUsage().getCompletionTokens() : 0)
                .totalTokens(aiResponse.getMetadata().getUsage().getTotalTokens() != null ? aiResponse.getMetadata().getUsage().getTotalTokens() : 0)
                .build();

        ChatResponse.Choice choice = ChatResponse.Choice.builder().index(0).message(new ChatMessage("assistant", context)).finishReason(aiResponse.getResult().getMetadata().getFinishReason()).build();
        return ChatResponse.builder().id(IdUtil.simpleUUID()).object("chat.completion")
                .created(System.currentTimeMillis() / 1000).choices(List.of(choice))
                .model(modelName).usage(usage).build();
    }

    /**
     * 构筑prompt
     * @param chatRequest 请求
     * @return prompt
     */
    private Prompt buildPrompt(ChatRequest chatRequest) {
        List<Message> list = chatRequest.getMessages().stream().map(msg -> switch (msg.getRole()) {
            case "system" -> new SystemMessage(msg.getContent());
            case "assistant" -> new AssistantMessage(msg.getContent());
            default -> new UserMessage(msg.getContent());
        }).collect(Collectors.toList());
        OpenAiChatOptions.Builder optionBuilder = OpenAiChatOptions.builder().model(chatRequest.getModel()).streamUsage(true);
        if (chatRequest.getTemperature() != null) {
            optionBuilder.temperature(chatRequest.getTemperature());
        }
        if (chatRequest.getMaxTokens() != null) {
            optionBuilder.maxTokens(chatRequest.getMaxTokens());
        }
        return new Prompt(list, optionBuilder.build());
    }

    @Override
    public Flux<String> chatStream(ChatRequest chatRequest, Long userId, Long apiKeyId) {
        String modelName = chatRequest.getModel();
        long startTime = System.currentTimeMillis();

        // Token 计数器和上次内容
        final int[] promptTokens = {0};
        final int[] completionTokens = {0};

        try {
            // 构建 Prompt
            Prompt prompt = buildPrompt(chatRequest);

            // 调用流式 AI 模型
            ChatClient chatClient = ChatClient.builder(chatModel).build();
            Flux<org.springframework.ai.chat.model.ChatResponse> flux = chatClient
                    .prompt(prompt)
                    .stream()
                    .chatResponse();

            return flux.flatMap(response -> {
                // 获取 Token 信息（通常只有最后一个 chunk 才有）
                if (response.getMetadata().getUsage() != null) {
                    Integer promptToken = response.getMetadata().getUsage().getPromptTokens();
                    Integer completion = response.getMetadata().getUsage().getCompletionTokens();

                    if (promptToken != null && promptToken > 0) {
                        promptTokens[0] = promptToken;
                    }
                    if (completion != null && completion > 0) {
                        completionTokens[0] = completion;
                    }
                }

                // 检查 result 是否为 null（最后一个 chunk 只有 token 信息，没有内容）
                if (ObjectUtils.isEmpty(response.getResult()) || ObjectUtils.isEmpty(response.getResult().getOutput())) {
                    // 跳过没有内容的 chunk
                    return Flux.empty();
                }
                String text = response.getResult().getOutput().getText();
                // ✅ 重点修复：null 安全处理，null -> 空字符串
                String content = Optional.ofNullable(text).orElse("");
                String escapedText = content.replace("\n", "\\n");
                // 如果是空文本，也跳过，避免推送空消息
                if (escapedText.isBlank()) {
                    return Flux.empty();
                }
                return Flux.just(escapedText + "\n\n");
            }).doOnComplete(() -> {
                // 流结束时记录日志
                long duration = System.currentTimeMillis() - startTime;
                int totalTokens = promptTokens[0] + completionTokens[0];

                requestLogService.logRequest(userId, apiKeyId, modelName,
                        promptTokens[0], completionTokens[0], totalTokens,
                        (int) duration, "success", null);
            }).doOnError(error -> {
                // 流错误时记录日志
                log.error("流式调用模型失败", error);
                long duration = System.currentTimeMillis() - startTime;
                requestLogService.logRequest(userId, apiKeyId, modelName, 0, 0, 0,
                        (int) duration, "failed", error.getMessage());
            });
        } catch (Exception e) {
            log.error("流式调用模型失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "流式调用模型失败: " + e.getMessage());
        }
    }



}
