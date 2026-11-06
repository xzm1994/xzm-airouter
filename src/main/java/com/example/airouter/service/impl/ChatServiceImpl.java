package com.example.airouter.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.poi.excel.sax.ElementName;
import com.example.airouter.exception.BusinessException;
import com.example.airouter.exception.ErrorCode;
import com.example.airouter.model.dto.chat.*;
import com.example.airouter.model.dto.requestlog.RequestLogDTO;
import com.example.airouter.model.entity.Model;
import com.example.airouter.model.entity.ModelProvider;
import com.example.airouter.service.*;
import jakarta.annotation.Resource;
import jodd.util.StringUtil;
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
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static cn.hutool.poi.excel.sax.ElementName.c;

@Service
@Slf4j
public class ChatServiceImpl implements ChatService {

    @Resource
    private RequestLogService requestLogService;

    @Resource
    private ModelProviderService modelProviderService;

    @Resource
    private ModelInvokeService modelInvokeService;

    @Resource
    private RoutingService routingService;

    private static final int MAX_FALLBACK_RETRIES = 3;

//    @Override
//    public ChatResponse chat(ChatRequest chatRequest, Long userId, Long apiKeyId) {
//        long startTime = System.currentTimeMillis();
//        String modelName = chatRequest.getModel();
//
//        // 构筑prompt
//        Prompt prompt = buildPrompt(chatRequest);
//
//        // 调用AI模型
//        ChatClient chatClient = ChatClient.builder(chatModel).build();
//        org.springframework.ai.chat.model.ChatResponse aiResponse = chatClient.prompt(prompt).call().chatResponse();
//        // 转换响应格式
//        assert aiResponse != null;
//        ChatResponse response = convertResponse(aiResponse, modelName);
//
//        // 记录请求日志
//        long duration = System.currentTimeMillis() - startTime;
//        requestLogService.logRequest(userId, apiKeyId, modelName, response.getUsage().getPromptTokens(),
//                response.getUsage().getCompletionTokens(), response.getUsage().getTotalTokens(), (int) duration, "success", null);
//        return response;
//    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest, Long userId, Long apiKeyId, String clientIp, String userAgent) {
        long startTime = System.currentTimeMillis();
        String modelName = chatRequest.getModel();
        // 设置唯一追踪id
        String traceId = IdUtil.simpleUUID();
        // 确定路由策略： 优先使用请求中指定的策略，否则根据是否指定模型决定
        String strategyType = determineStrategyType(chatRequest.getRoutingStrategy(), modelName);

        // 选择模型
        Model selectModel = routingService.selectModel(strategyType, "chat", modelName);
        if (ObjUtil.isNull(selectModel)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "没有可用的模型");
        }

        // 获取fallback 模型列表
        List<Model> fallbackModels = routingService.getFallbackModels(strategyType, " chat", modelName);
        // 尝试调用主模型
        boolean isFallback = false;
        try {
            return invokeModelWithFallback(selectModel, fallbackModels, chatRequest, userId, apiKeyId, traceId, startTime, strategyType, isFallback, clientIp, userAgent);
        } catch (Exception e) {
            log.error("所有模型调用失败", e);
            long duration = System.currentTimeMillis() - startTime;
            // 记录失败日志
            requestLogService.logRequest(RequestLogDTO.builder()
                    .traceId(traceId).userId(userId).apiKeyId(apiKeyId)
                    .requestModel(modelName).requestType("chat").source(apiKeyId != null ? "api" : "web")
                    .duration((int) duration).status("failed").errorMessage(e.getMessage()).errorCode("SYSTEM_ERROR")
                    .routingStrategy(strategyType).isFallback(false).clientIp(clientIp).userAgent(userAgent).build());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "调用模型失败：" + e.getMessage());
        }
    }

    @Override
    public Flux<StreamResponse> chatStream(ChatRequest chatRequest, Long userId, Long apiKeyId, String clientIp, String userAgent) {
        String requestedModel = chatRequest.getModel();
        long startTime = System.currentTimeMillis();
        String traceId = IdUtil.simpleUUID();
        long created = System.currentTimeMillis() / 1000;


        try{
            // Token 计数器
            final int[] promptTokens = {0};
            final int[] completionTokens = {0};
            final boolean[] isFirstChunk = {true};
            // 确定路由策略：优先使用请求中指定的策略，否则根据是否指定模型决定
            String strategyType = determineStrategyType(chatRequest.getRoutingStrategy(), requestedModel);
            Model selectModel = routingService.selectModel(strategyType, "chat", requestedModel);
            if (ObjUtil.isNull(selectModel)) {
                return Flux.error(new BusinessException(ErrorCode.PARAMS_ERROR, "没有可用的模型"));
            }
            // 获取提供者信息
            ModelProvider modelProvider = modelProviderService.getById(selectModel.getProviderId());
            if (ObjUtil.isNull(modelProvider)) {
                return Flux.error(new BusinessException(ErrorCode.PARAMS_ERROR, "模型提供者不存在"));
            }
            Flux<StreamChunk> chunkFlux = modelInvokeService.invokeStreamChunk(selectModel, modelProvider, chatRequest);
            // 将统一格式的响应块转换为 OpenAI SSE 格式的 StreamResponse
            return chunkFlux.flatMap(chunk -> {
                // 更新token统计
                if (chunk.getPromptTokens() != null && chunk.getPromptTokens() > 0) {
                    promptTokens[0] = chunk.getPromptTokens();
                }
                if (chunk.getCompletionTokens() != null && chunk.getCompletionTokens() > 0) {
                    completionTokens[0] = chunk.getCompletionTokens();
                }
                // 构建Delta
                StreamResponse.Delta.DeltaBuilder deltaBuilder = StreamResponse.Delta.builder();

                // 第一个快包含role
                if (isFirstChunk[0]) {
                    deltaBuilder.role("assistant");
                    isFirstChunk[0] = false;
                }

                // 处理普通文本
                if (chunk.hasText()) {
                    deltaBuilder.content(chunk.getText());
                }

                // 处理深度思考内容
                if (chunk.hasReasoningContent()){
                    deltaBuilder.reasoningContent(chunk.getReasoningContent());
                }

                // 如果没有文本也没有思考内容，跳过

                if (!chunk.hasText() && !chunk.hasReasoningContent()){
                    return Flux.empty();
                }

                StreamResponse.Delta delta = deltaBuilder.build();
                StreamResponse.StreamChoice choice = StreamResponse.StreamChoice.builder().index(0).delta(delta).finishReason(null).build();
                return Flux.just(StreamResponse.builder().id(traceId).object("chat.completion.chunk")
                        .created(created).choices(List.of(choice)).model(selectModel.getModelKey()).build());
            }).concatWith(Flux.defer(() -> {
                StreamResponse.StreamChoice choice = StreamResponse.StreamChoice.builder().index(0).delta(StreamResponse.Delta.builder().build()).finishReason("stop").build();
                return Flux.just(StreamResponse.builder().id(traceId)
                        .object("chat.completion.chunk").created(created)
                        .model(selectModel.getModelKey()).choices(List.of(choice)).build());
            })).doOnComplete(() -> {
                // 流结束时候记录日志
                long duration = System.currentTimeMillis() - startTime;
                int totalTokens = promptTokens[0] + completionTokens[0];
                requestLogService.logRequest(RequestLogDTO.builder()
                        .traceId(traceId)
                        .userId(userId)
                        .apiKeyId(apiKeyId)
                        .modelId(selectModel.getId())
                        .requestModel(selectModel.getModelKey())
                        .requestType("chat")
                        .source(apiKeyId != null ? "api" : "web")
                        .promptTokens(promptTokens[0])
                        .completionTokens(completionTokens[0])
                        .totalTokens(totalTokens)
                        .duration((int) duration)
                        .status("success")
                        .routingStrategy(strategyType)
                        .isFallback(false)
                        .clientIp(clientIp)
                        .userAgent(userAgent)
                        .build());
            }).doOnError(error -> {
                // 流错误时记录日志
                log.error("流式调用模型失败", error);
                long duration = System.currentTimeMillis() - startTime;
                requestLogService.logRequest(RequestLogDTO.builder()
                        .traceId(traceId)
                        .userId(userId)
                        .apiKeyId(apiKeyId)
                        .modelId(selectModel.getId())
                        .requestModel(selectModel.getModelKey())
                        .requestType("chat")
                        .source(apiKeyId != null ? "api" : "web")
                        .duration((int) duration)
                        .status("failed")
                        .errorMessage(error.getMessage())
                        .errorCode("STREAM_ERROR")
                        .routingStrategy(strategyType)
                        .isFallback(false)
                        .clientIp(clientIp)
                        .userAgent(userAgent)
                        .build());
            });
        } catch (Exception e) {
            log.error("流式调用模型失败", e);
            return Flux.error(new BusinessException(ErrorCode.SYSTEM_ERROR, "流式调用模型失败: " + e.getMessage()));
        }
    }

    /**
     * 带fallback的模型调用
     *
     * @param selectModel
     * @param fallbackModels
     * @param chatRequest
     * @param userId
     * @param apiKeyId
     * @param traceId
     * @param startTime
     * @param strategyType
     * @param isFallback
     * @param clientIp
     * @param userAgent
     * @return
     */
    private ChatResponse invokeModelWithFallback(Model selectModel, List<Model> fallbackModels,
                                                 ChatRequest chatRequest, Long userId, Long apiKeyId,
                                                 String traceId, long startTime, String strategyType,
                                                 boolean isFallback, String clientIp, String userAgent) {

        // 尝试调用主模型
        try {
            return callModel(selectModel, chatRequest, userId, apiKeyId, traceId, startTime, strategyType, isFallback, clientIp, userAgent);
        } catch (Exception e) {
            log.info("模型{}调用失败，尝试fallback", selectModel.getModelKey(), e);
            // 如果主模型失败且有fallbback模型，尝试fallback
            if (!CollectionUtils.isEmpty(fallbackModels)) {

                int retries = Math.min(fallbackModels.size(), MAX_FALLBACK_RETRIES);
                for (int i = 0; i < retries; i++) {
                    Model model = fallbackModels.get(i);
                    try {
                        log.info("尝试fallback模型：{}", model.getModelKey());
                        return callModel(model, chatRequest, userId, apiKeyId, traceId, startTime, strategyType, isFallback, clientIp, userAgent);
                    } catch (Exception ex) {
                        log.info("fallback模型{}调用失败", model.getModelKey(), ex);
                        if (i == retries - 1) {
                            throw ex;
                        }
                    }
                }
            }
            throw e;
        }
    }

    /**
     * 嗲用单个模型
     *
     * @param selectModel
     * @param chatRequest
     * @param userId
     * @param apiKeyId
     * @param traceId
     * @param startTime
     * @param strategyType
     * @param isFallback
     * @param clientIp
     * @param userAgent
     * @return
     */
    private ChatResponse callModel(Model selectModel, ChatRequest chatRequest, Long userId,
                                   Long apiKeyId, String traceId, long startTime, String strategyType,
                                   boolean isFallback, String clientIp, String userAgent) {
        // 获取提供者信息
        ModelProvider provider = modelProviderService.getById(selectModel.getProviderId());
        if (provider == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模型提供者不存在");
        }

        // 调用模型
        org.springframework.ai.chat.model.ChatResponse chatResponse = modelInvokeService.invoke(selectModel, provider, chatRequest);
        // 转换响应格式
        ChatResponse response = convertResponse(chatResponse, selectModel.getModelKey());
        // 记录请求日志
        long duration = System.currentTimeMillis() - startTime;
        int totalTokens = response.getUsage().getTotalTokens();
        requestLogService.logRequest(RequestLogDTO.builder()
                .traceId(traceId)
                .userId(userId)
                .apiKeyId(apiKeyId)
                .modelId(selectModel.getId())
                .requestModel(selectModel.getModelKey())
                .requestType("chat")
                .source(apiKeyId != null ? "api" : "web")
                .promptTokens(response.getUsage().getPromptTokens())
                .completionTokens(response.getUsage().getCompletionTokens())
                .totalTokens(totalTokens)
                .duration((int) duration)
                .status("success")
                .routingStrategy(strategyType)
                .isFallback(isFallback)
                .clientIp(clientIp)
                .userAgent(userAgent)
                .build());
        return response;
    }


    /**
     * 确定路由策略 优先使用请求中指定的策略，否则根据是否指定模型决定
     *
     * @param routingStrategy
     * @param modelName
     * @return
     */
    private String determineStrategyType(String routingStrategy, String modelName) {
        // 如果请求中指定了策略，优先使用
        if (StringUtil.isNotEmpty(routingStrategy)) {
            return routingStrategy;
        }
        // 如果指定了模型单没有指定策略，使用固定模型策略
        if (StrUtil.isNotBlank(modelName)) {
            return "fixed";
        }
        return "auto";
    }

    /**
     * 转化响应格式
     *
     * @param aiResponse ai返回的响应
     * @param modelName  模型名称
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
     *
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

//    @Override
//    public Flux<String> chatStream(ChatRequest chatRequest, Long userId, Long apiKeyId) {
//        String modelName = chatRequest.getModel();
//        long startTime = System.currentTimeMillis();
//
//        // Token 计数器和上次内容
//        final int[] promptTokens = {0};
//        final int[] completionTokens = {0};
//
//        try {
//            // 构建 Prompt
//            Prompt prompt = buildPrompt(chatRequest);
//
//            // 调用流式 AI 模型
//            ChatClient chatClient = ChatClient.builder(chatModel).build();
//            Flux<org.springframework.ai.chat.model.ChatResponse> flux = chatClient
//                    .prompt(prompt)
//                    .stream()
//                    .chatResponse();
//
//            return flux.flatMap(response -> {
//                // 获取 Token 信息（通常只有最后一个 chunk 才有）
//                if (response.getMetadata().getUsage() != null) {
//                    Integer promptToken = response.getMetadata().getUsage().getPromptTokens();
//                    Integer completion = response.getMetadata().getUsage().getCompletionTokens();
//
//                    if (promptToken != null && promptToken > 0) {
//                        promptTokens[0] = promptToken;
//                    }
//                    if (completion != null && completion > 0) {
//                        completionTokens[0] = completion;
//                    }
//                }
//
//                // 检查 result 是否为 null（最后一个 chunk 只有 token 信息，没有内容）
//                if (ObjectUtils.isEmpty(response.getResult()) || ObjectUtils.isEmpty(response.getResult().getOutput())) {
//                    // 跳过没有内容的 chunk
//                    return Flux.empty();
//                }
//                String text = response.getResult().getOutput().getText();
//                // ✅ 重点修复：null 安全处理，null -> 空字符串
//                String content = Optional.ofNullable(text).orElse("");
//                String escapedText = content.replace("\n", "\\n");
//                // 如果是空文本，也跳过，避免推送空消息
//                if (escapedText.isBlank()) {
//                    return Flux.empty();
//                }
//                return Flux.just(escapedText + "\n\n");
//            }).doOnComplete(() -> {
//                // 流结束时记录日志
//                long duration = System.currentTimeMillis() - startTime;
//                int totalTokens = promptTokens[0] + completionTokens[0];
//
//                requestLogService.logRequest(userId, apiKeyId, modelName,
//                        promptTokens[0], completionTokens[0], totalTokens,
//                        (int) duration, "success", null);
//            }).doOnError(error -> {
//                // 流错误时记录日志
//                log.error("流式调用模型失败", error);
//                long duration = System.currentTimeMillis() - startTime;
//                requestLogService.logRequest(userId, apiKeyId, modelName, 0, 0, 0,
//                        (int) duration, "failed", error.getMessage());
//            });
//        } catch (Exception e) {
//            log.error("流式调用模型失败", e);
//            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "流式调用模型失败: " + e.getMessage());
//        }
//    }


}
