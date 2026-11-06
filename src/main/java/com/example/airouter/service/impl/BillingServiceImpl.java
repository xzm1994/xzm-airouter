package com.example.airouter.service.impl;

import com.example.airouter.model.entity.Model;
import com.example.airouter.service.BillingService;
import com.example.airouter.service.ModelService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class BillingServiceImpl implements BillingService {
    @Resource
    private ModelService  modelService;

    /**
     * 每千Token的价格基数
     */
    private static final BigDecimal TOKENS_PER_UNIT = new BigDecimal("1000");

    @Override
    public BigDecimal calculateCost(Model model, int promptTokens, int completionTokens) {
        if (model == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal inputPrice = model.getInputPrice();
        BigDecimal outputPrice = model.getOutputPrice();
        if (inputPrice == null){
            inputPrice = BigDecimal.ZERO;
        }
        if (outputPrice == null){
            outputPrice = BigDecimal.ZERO;
        }

        // 费用= （输入token数*输入价格 + 输出token数*输出价格）/1000
        BigDecimal inputCost = inputPrice.multiply(new BigDecimal(promptTokens)).divide(TOKENS_PER_UNIT, 6, RoundingMode.HALF_UP);
        BigDecimal outputCost = outputPrice.multiply(new BigDecimal(completionTokens)).divide(TOKENS_PER_UNIT, 6, RoundingMode.HALF_UP);
        return inputCost.add(outputCost);
    }

    @Override
    public BigDecimal calculateCost(Long modelId, int promptTokens, int completionTokens) {
        if (modelId == null) {
            return BigDecimal.ZERO;
        }
        Model model = modelService.getById(modelId);
        return this.calculateCost(model, promptTokens, completionTokens);
    }
}
