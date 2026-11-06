package com.example.airouter.controller;

import cn.hutool.extra.servlet.JakartaServletUtil;
import com.example.airouter.annotation.AuthCheck;
import com.example.airouter.common.ResultUtils;
import com.example.airouter.constant.UserConstant;
import com.example.airouter.exception.BusinessException;
import com.example.airouter.exception.ErrorCode;
import com.example.airouter.model.dto.chat.ChatRequest;
import com.example.airouter.model.dto.chat.ChatResponse;
import com.example.airouter.model.entity.ApiKey;
import com.example.airouter.model.entity.User;
import com.example.airouter.service.ApiKeyService;
import com.example.airouter.service.ChatService;
import com.example.airouter.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/chat")
@Slf4j
public class InternalChatController {

    @Resource
    private ChatService chatService;

    @Resource
    private ApiKeyService apiKeyService;

    @Resource
    private UserService userService;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 内部聊天接口（使用 API Key ID）
     * 支持流式和非流式响应
     */
    @PostMapping(value = "/completions", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    @AuthCheck(value = UserConstant.DEFAULT_ROLE)
    @Operation(summary = "内部聊天接口")
    public Object chatCompletions(@RequestBody ChatRequest request,
                                   @RequestParam Long apiKeyId,
                                   HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);

        // 1. 验证 API Key 归属
        ApiKey apiKey = apiKeyService.getById(apiKeyId);
        if (apiKey == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "API Key 不存在");
        }

        if (!apiKey.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权使用该 API Key");
        }

        if (!"active".equals(apiKey.getStatus())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "API Key 已失效");
        }

        // 2. 参数校验
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "messages 不能为空");
        }

        // 3. 设置默认模型（如果未指定）
        if (request.getModel() == null || request.getModel().isEmpty()) {
            request.setModel("qwen-plus");
        }
        // 2. 获取客户端IP和User-Agent
        String clientIp = JakartaServletUtil.getClientIP(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");


        // 4. 判断是否为流式请求
        Boolean stream = request.getStream();
        if (stream != null && stream) {
            // 流式响应（网页端调用，不传 apiKeyId）- 转换为 SSE 格式
            return chatService.chatStream(request, loginUser.getId(), null, clientIp, userAgent)
                    .map(streamResponse -> {
                        try {
                            // 将 StreamResponse 转换为 JSON
                            String json = objectMapper.writeValueAsString(streamResponse);
                            return json + "\n\n";
                        } catch (Exception e) {
                            log.error("Failed to serialize stream response", e);
                            return "";
                        }
                    });
        } else {
            // 非流式响应
            return chatService.chat(request, apiKey.getUserId(), apiKey.getId(), clientIp, userAgent);
        }
    }
}
