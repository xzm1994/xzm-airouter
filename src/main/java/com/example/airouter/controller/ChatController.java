package com.example.airouter.controller;

import com.example.airouter.exception.BusinessException;
import com.example.airouter.exception.ErrorCode;
import com.example.airouter.model.dto.chat.ChatRequest;
import com.example.airouter.model.entity.ApiKey;
import com.example.airouter.service.ApiKeyService;
import com.example.airouter.service.ChatService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/chat")
@Slf4j
public class ChatController {
    @Resource
    private ChatService chatService;

    @Resource
    private ApiKeyService  apiKeyService;

    @PostMapping(value = "/completions", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public Object chatCompletions(@RequestBody ChatRequest request,
                                  @RequestHeader(value = "Authorization", required = false)String authorization,
                                  HttpServletRequest httpRequest){

        // 1.验证 API key
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "缺少或无效的 Authorization Header");
        }
        String apiKeyValue = authorization.substring(7);
        ApiKey apiKey = apiKeyService.getByKeyValue(apiKeyValue);
        if (apiKey == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "API key无效或失效");
        }
        // 2. 参数校验
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "messages 不能为空");
        }
        // 3. 设置默认模型
        if (request.getModel() == null || request.getModel().isEmpty()) {
            request.setModel("qwen-plus");
        }
        // 4.判断是否为流式请求
        Boolean stream = request.getStream();
        if (stream != null && stream) {
            return chatService.chatStream(request, apiKey.getUserId(), apiKey.getId());
        } else  {
            return chatService.chat(request, apiKey.getUserId(), apiKey.getId());
        }
    }
}
