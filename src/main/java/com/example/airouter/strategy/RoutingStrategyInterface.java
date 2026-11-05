package com.example.airouter.strategy;

import com.example.airouter.model.entity.Model;

import java.util.List;

/**
 * 路由策略接口
 */
public interface RoutingStrategyInterface {

    /**
     * 选择最优模型（从数据库查询并返回最符合策略的一条数据）
     * @param modelType
     * @param requestedModel
     * @return Model
     */
    Model selectModel(String modelType, String requestedModel);

    /**
     * 获取Fallback模型列表（除了主选模型外的备选模型）
     * @param modelType
     * @param requestedModel
     * @return List<Model>
     */
    List<Model> getFallbackModels(String modelType, String requestedModel);

    /**
     * 获取策略类型
     * @return String
     */
    String getStrategyType();
}
