package com.example.airouter.service;

import com.example.airouter.model.entity.ModelProvider;
import com.mybatisflex.core.service.IService;

import java.math.BigDecimal;

/**
 * 模型提供商服务
 */
public interface ModelProviderService extends IService<ModelProvider> {
    /**
     * 更新健康状态
     *
     * @param providerId    提供者ID
     * @param healthStatus  健康状态
     * @param avgLatency    平均延迟
     * @param avgSuccessRate   成功率
     */
    void updateHealthStatus(Long providerId, String healthStatus, int avgLatency, BigDecimal avgSuccessRate);
}
