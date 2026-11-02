package com.example.airouter.service.impl;

import com.example.airouter.mapper.RequestLogMapper;
import com.example.airouter.model.entity.RequestLog;
import com.example.airouter.service.ApiKeyService;
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

    @Override
    public void logRequest(Long userId, Long apiKeyId, String modelName,
                           Integer promptTokens, Integer completionTokens,
                           Integer totalTokens, Integer duration, String status, String errorMessage) {
        // 创建请求日志
        RequestLog log = RequestLog.builder()
                .userId(userId)
                .apiKeyId(String.valueOf(apiKeyId))
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
