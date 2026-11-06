package com.example.airouter.service.impl;

import cn.hutool.core.util.StrUtil;
import com.example.airouter.model.entity.Model;
import com.example.airouter.model.enums.RoutingStrategyTypeEnum;
import com.example.airouter.service.RoutingService;
import com.example.airouter.strategy.RoutingStrategyInterface;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class RoutingServiceImpl implements RoutingService {
    @Resource
    private List<RoutingStrategyInterface> routingStrategies;

    @Override
    public Model selectModel(String strategyType, String modelType, String requestModel) {
        RoutingStrategyInterface strategy = getStrategy(strategyType);
        if (strategy == null) {
            // 默认使用自动路由策略
            strategy = getStrategy(RoutingStrategyTypeEnum.AUTO.getValue());
        }
        return strategy.selectModel(modelType, requestModel);
    }

    @Override
    public List<Model> getFallbackModels(String strategyType, String modelType, String modelName) {
        RoutingStrategyInterface strategy = getStrategy(strategyType);
        if (strategy == null) {
            strategy = getStrategy(RoutingStrategyTypeEnum.AUTO.getValue());
        }
        if (strategy == null) {
            return Collections.emptyList();
        }
        return strategy.getFallbackModels(modelType, modelName);
    }

    /**
     * 获取策略
     * @param strategyType
     * @return
     */
    private RoutingStrategyInterface getStrategy(String strategyType) {
        if (StrUtil.isBlank(strategyType)) {
            return null;
        }
        return routingStrategies.stream()
                .filter(strategy -> strategy.getStrategyType().equals(strategyType))
                .findFirst().orElse(null);
    }
}
