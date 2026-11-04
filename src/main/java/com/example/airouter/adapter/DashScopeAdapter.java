package com.example.airouter.adapter;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.example.airouter.exception.BusinessException;
import com.example.airouter.exception.ErrorCode;
import com.example.airouter.model.dto.chat.ChatMessage;
import com.example.airouter.model.dto.chat.ChatRequest;
import com.example.airouter.model.dto.chat.StreamChunk;
import com.example.airouter.model.entity.Model;
import com.example.airouter.model.entity.ModelProvider;
import jodd.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DashScopeAdapter implements ModelAdapter{
    /**
     * 支持的提供者名称
     */
    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("qwen", "dashscope","tongyi","aliyun");

    @Override
    public ChatResponse invoke(Model model, ModelProvider provider, ChatRequest chatRequest) {
        try {
            DashScopeChatModel chatModel = createChatModel(provider, model, chatRequest);
            Prompt prompt = buildPrompt(chatRequest);
            return chatModel.call(prompt);
        } catch (Exception e) {
            log.error("DashScope 适配器调用模型 {} 失败", model.getModelKey(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "调用模型失败: " + e.getMessage());
        }
    }




    @Override
    public Flux<ChatResponse> invokeStream(Model model, ModelProvider provider, ChatRequest chatRequest) {
        try {
            DashScopeChatModel chatModel = createChatModel(provider, model, chatRequest);

            Prompt prompt = buildPrompt(chatRequest);
            return chatModel.stream(prompt);
        } catch (Exception e) {
            log.error("DashScope 适配器流式调用模型 {} 失败", model.getModelKey(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "流式调用模型失败: " + e.getMessage());
        }
    }

    @Override
    public Flux<StreamChunk> invokeStreamChunk(Model model, ModelProvider provider, ChatRequest chatRequest) {
        return invokeStream(model, provider, chatRequest)
                .map(this::convertToStreamChunk)
                .filter(chunk -> !chunk.isEmpty());
    }


    /**
     * 将Dashscope响应转化为统一的streamchunk格式
     * 通义千问深度思考内容存储在 metadata 的 "reasoningContent" 字段
     * @param chatResponse 响应结果
     * @return StreamChunk
     */
    private StreamChunk convertToStreamChunk(ChatResponse chatResponse) {
        StreamChunk.StreamChunkBuilder builder = StreamChunk.builder();
        // 提取token信息
        if(ObjectUtils.isNotEmpty(chatResponse.getMetadata()) && chatResponse.getMetadata().getUsage() != null) {
            Usage usage = chatResponse.getMetadata().getUsage();
            Integer promptTokens = usage.getPromptTokens();
            Integer completionTokens = usage.getCompletionTokens();
            if (promptTokens != null && promptTokens > 0) {
                builder.promptTokens(promptTokens);
            }
            if (completionTokens != null && completionTokens > 0) {
                builder.completionTokens(completionTokens);
            }
        }
        // 提取内容
        if (ObjectUtils.isNotEmpty(chatResponse.getResult()) && ObjectUtils.isNotEmpty(chatResponse.getResult().getOutput())){
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            Map<String, Object> metadata = assistantMessage.getMetadata();
            if (ObjectUtils.isNotEmpty(metadata)) {
                Object reasoningContent = metadata.get("reasoningContent");
                if (ObjectUtils.isNotEmpty(reasoningContent) && !reasoningContent.toString().isEmpty()) {
                    builder.reasoningContent(reasoningContent.toString());
                }
            }
            // 提取普通文本内容
            String text = assistantMessage.getText();
            if (StringUtil.isNotEmpty(text)) {
                builder.text(text);
            }
        }
        StreamChunk chunk = builder.build();
        // 如果没有任何内容，标记为空
        if (!chunk.hasText() && !chunk.hasReasoningContent() && chunk.getPromptTokens() == null && chunk.getCompletionTokens() == null) {
            return StreamChunk.empty();
        }
        return chunk;
    }

    @Override
    public boolean supports(String providerName) {
        return providerName != null && SUPPORTED_PROVIDERS.contains(providerName.toLowerCase());
    }


    /**
     * 创建DashScopeChatModel
     * @param provider 提供商
     * @param model 模型
     * @param chatRequest 请求
     * @return DashScopeChatModel
     */
    private DashScopeChatModel createChatModel(ModelProvider provider, Model model, ChatRequest chatRequest) {
        DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(provider.getApiKey()).build();
        DashScopeChatOptions options = buildOptions(model, chatRequest);
        return DashScopeChatModel.builder().dashScopeApi(dashScopeApi).defaultOptions(options).build();
    }

    /**
     * 构筑dashscope选项
     * @param model 模型
     * @param chatRequest 请求
     * @return DashScopeChatOptions
     */
    private DashScopeChatOptions buildOptions(Model model, ChatRequest chatRequest) {
        DashScopeChatOptions options = new DashScopeChatOptions();
        options.setModel(model.getModelKey());
        if (chatRequest.getTemperature() != null) {
            options.setTemperature(chatRequest.getTemperature());
        }
        if (chatRequest.getMaxTokens() != null) {
            options.setMaxTokens(chatRequest.getMaxTokens());
        }

        // 通义千问深度思考支持
        if (chatRequest.getEnableReasoning() != null
                && chatRequest.getEnableReasoning()
                && model.getSupportReasoning() != null
                && model.getSupportReasoning()) {
            options.setEnableThinking(true);
        }
        return options;
    }

    /**
     * 构建prompt
     * @param chatRequest 请求
     * @return Prompt
     */
    private Prompt buildPrompt(ChatRequest chatRequest) {
        List<Message> messages = chatRequest.getMessages().stream().map(this::convertMessage).toList();
        return new Prompt(messages);
    }

    /**
     * 转换消息类型
     * @param chatMessage
     * @return
     */
    private Message convertMessage(ChatMessage chatMessage) {
        String role = chatMessage.getRole();
        if ("system".equals(role)) {
            return new SystemMessage(chatMessage.getContent());
        } else if ("assistant".equals(role)) {
            return new AssistantMessage(chatMessage.getContent());
        } else {
            return new UserMessage(chatMessage.getContent());
        }
    }
}
