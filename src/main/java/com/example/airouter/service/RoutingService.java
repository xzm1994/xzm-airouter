package com.example.airouter.service;

import com.example.airouter.model.entity.Model;

import java.util.List;

/**
 * 路由服务
 */
public interface RoutingService {

    /**
     * 根据策略选择模型
     * @param strategyType 策略
     * @param modelType 模型类型（可选）
     * @param requestModel 用户请求的模型标识（固定模型策略使用）
     * @return 选中的模型
     */
    Model selectModel(String strategyType, String modelType, String requestModel);

    /**
     * 获取 Fallback 模型列表
     * @param strategyType 策略
     * @param modelType 模型类型（可选）
     * @param modelName 用户请求的模型标识（固定模型策略使用）
     * @return List<Model>
     */
    List<Model> getFallbackModels(String strategyType, String modelType, String modelName);
}
