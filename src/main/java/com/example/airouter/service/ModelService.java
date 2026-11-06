package com.example.airouter.service;

import com.example.airouter.model.entity.Model;
import com.mybatisflex.core.service.IService;

import java.math.BigDecimal;
import java.util.List;

/**
 * 模型服务接口
 */
public interface ModelService extends IService<Model> {

    List<Model> getActiveModels();

    /**
     * 更新模型的健康指标
     *
     * @param modelId      模型ID
     * @param healthStatus 健康状态
     * @param avgLatency   平均延迟（毫秒）
     * @param successRate  成功率（百分比）
     * @param score        综合得分
     */
    void updateModelMetrics(Long modelId, String healthStatus, int avgLatency, BigDecimal successRate, BigDecimal score);
}
