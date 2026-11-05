package com.example.airouter.strategy.impl;

import com.example.airouter.model.entity.Model;
import com.example.airouter.model.enums.HealthStatusEnum;
import com.example.airouter.model.enums.ModelStatusEnum;
import com.example.airouter.model.enums.RoutingStrategyTypeEnum;
import com.example.airouter.service.ModelService;
import com.example.airouter.strategy.RoutingStrategyInterface;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import jodd.util.StringUtil;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 延迟优先路由策略
 * 选择延迟最低的健康模型
 */
@Component
public class LatencyFirstRoutingStrategy implements RoutingStrategyInterface {
    @Resource
    private ModelService modelService;
    @Override
    public Model selectModel(String modelType, String requestedModel) {
        QueryWrapper queryWrapper = buildBaseQueryWrapper(modelType);
        queryWrapper.orderBy("CASE WHEN avgLatency = 0 THEN 999999 ELSE avgLatency END", true);
        queryWrapper.limit(1);
        return modelService.getOne(queryWrapper);
    }

    @Override
    public List<Model> getFallbackModels(String modelType, String requestedModel) {
        QueryWrapper queryWrapper = buildBaseQueryWrapper(modelType);
        queryWrapper.orderBy("CASE WHEN avgLatency = 0 THEN 999999 ELSE avgLatency END", true);
        List<Model> models = modelService.list(queryWrapper);
        return models.stream().skip(1).toList();
    }

    @Override
    public String getStrategyType() {
        return RoutingStrategyTypeEnum.LATENCY_FIRST.getValue();
    }


    /**
     * 构筑基础查询条件：状态为启用且健康状态为健康或降级或未知
     *
     * @param modelType
     * @return QueryWrapper
     */
    private QueryWrapper buildBaseQueryWrapper(String modelType) {
        QueryWrapper queryWrapper = QueryWrapper.create().eq("status", ModelStatusEnum.ACTIVE.getValue())
                .eq("healthStatus", HealthStatusEnum.HEALTHY.getValue());
        if (StringUtil.isNotEmpty(modelType)) {
            queryWrapper.eq("modelType", modelType);
        }
        return queryWrapper;
    }
}
