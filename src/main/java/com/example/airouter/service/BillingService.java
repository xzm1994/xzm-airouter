package com.example.airouter.service;

import com.example.airouter.model.entity.Model;

import java.math.BigDecimal;

/**
 * 费用计算服务
 */
public interface BillingService {

    /**
     * 计算单次请求费用
     *
     * @param model            模型
     * @param promptTokens     输入Token数
     * @param completionTokens 输出Token数
     * @return 费用（元）
     */
    BigDecimal calculateCost(Model model,int promptTokens, int completionTokens);

    /**
     * 根据模型ID计算费用
     *
     * @param modelId          模型ID
     * @param promptTokens     输入Token数
     * @param completionTokens 输出Token数
     * @return 费用（元）
     */
    BigDecimal calculateCost(Long modelId, int promptTokens, int completionTokens);
}
