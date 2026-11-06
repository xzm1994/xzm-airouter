package com.example.airouter.service.impl;

import cn.hutool.core.util.IdUtil;
import com.example.airouter.mapper.RequestLogMapper;
import com.example.airouter.model.dto.requestlog.RequestLogDTO;
import com.example.airouter.model.entity.RequestLog;
import com.example.airouter.service.ApiKeyService;
import com.example.airouter.service.BillingService;
import com.example.airouter.service.RequestLogService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RequestLogServiceImpl extends ServiceImpl<RequestLogMapper, RequestLog> implements RequestLogService {
    @Resource
    private RequestLogMapper requestLogMapper;
    @Resource
    private ApiKeyService apiKeyService;

    @Resource
    private BillingService billingService;

    @Override
    public void logRequest(RequestLogDTO logDTO) {
        if (logDTO == null) {
            return;
        }
        // 如果没有traceId，生成一个
        if (logDTO.getTraceId() == null) {
            logDTO.setTraceId(IdUtil.simpleUUID());
        }
        // todo 计算费用（如果没有提供费用，则自动计算）
        BigDecimal cost = logDTO.getCost();
        if (cost == null && logDTO.getModelId() != null && "success".equals(logDTO.getStatus())) {
            int promptTokens = logDTO.getPromptTokens() != null ? logDTO.getPromptTokens() : 0;
            int completionTokens = logDTO.getCompletionTokens() != null ? logDTO.getCompletionTokens() : 0;
            cost = billingService.calculateCost(logDTO.getModelId(), promptTokens, completionTokens);
        }
        // 创建请求日志
        RequestLog requestLog = RequestLog.builder().traceId(logDTO.getTraceId()).userId(logDTO.getUserId())
                .apiKeyId(logDTO.getApiKeyId()).modelId(logDTO.getModelId())
                .requestModel(logDTO.getRequestModel()).modelName(logDTO.getRequestModel()).requestType(logDTO.getRequestType())
                .source(logDTO.getSource()).promptTokens(logDTO.getPromptTokens()).completionTokens(logDTO.getCompletionTokens())
                .totalTokens(logDTO.getTotalTokens()).cost(cost).duration(logDTO.getDuration()).status(logDTO.getStatus())
                .errorMessage(logDTO.getErrorMessage()).errorCode(logDTO.getErrorCode()).routingStrategy(logDTO.getRoutingStrategy())
                .isFallback(logDTO.getIsFallback() != null && logDTO.getIsFallback() ? 1 : 0)
                .clientIp(logDTO.getClientIp()).userAgent(logDTO.getUserAgent())
                .createTime(LocalDateTime.now()).updateTime(LocalDateTime.now()).build();

        // 保存日志
        requestLogMapper.insert(requestLog);

        // 更新 api key 的使用统计（仅成功的请求且有apikeyid）
        if ("success".equals(logDTO.getStatus()) && logDTO.getApiKeyId() != null && logDTO.getTotalTokens() != null && logDTO.getTotalTokens() > 0) {
            apiKeyService.updateUsageStats(logDTO.getApiKeyId(), logDTO.getTotalTokens());

        }

    }

    @Override
    public void logRequest(Long userId, Long apiKeyId, String modelName,
                           Integer promptTokens, Integer completionTokens,
                           Integer totalTokens, Integer duration, String status, String errorMessage) {
        // 创建请求日志
        RequestLog log = RequestLog.builder()
                .userId(userId)
                .apiKeyId(apiKeyId)
                .modelName(modelName)
                .requestType("chat")
                .source("web")
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .duration(duration)
                .isFallback(0)
                .status(status)
                .cost(new BigDecimal("0.1"))
                .errorMessage(errorMessage)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        // 保存日志
        requestLogMapper.insert(log);

        // 更新 API Key 的使用统计（仅成功的请求）
        if ("success".equals(status) && apiKeyId != null && totalTokens != null && totalTokens > 0) {
            apiKeyService.updateUsageStats(apiKeyId, totalTokens);
        }
    }

    @Override
    public List<RequestLog> listUserLogs(Long userId, Integer limit) {
        QueryWrapper queryWrapper = QueryWrapper.create().eq("userId", userId).orderBy("createTime", false).limit(limit != null ? limit : 100);
        return requestLogMapper.selectListByQuery(queryWrapper);
    }

    @Override
    public Long countUserTokens(Long userId) {
        QueryWrapper queryWrapper = QueryWrapper.create().select("SUM(totalTokens)").eq("userId", userId).eq("status", "success");
        Long total = requestLogMapper.selectObjectByQueryAs(queryWrapper, Long.class);
        return total != null ? total : 0L;
    }
}
