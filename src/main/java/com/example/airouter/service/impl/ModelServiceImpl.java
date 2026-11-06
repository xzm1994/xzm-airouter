package com.example.airouter.service.impl;

import com.example.airouter.mapper.ModelMapper;
import com.example.airouter.model.entity.Model;
import com.example.airouter.model.enums.ModelStatusEnum;
import com.example.airouter.service.ModelService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
public class ModelServiceImpl extends ServiceImpl<ModelMapper, Model> implements ModelService {
    @Override
    public List<Model> getActiveModels() {
        return this.list(QueryWrapper.create()
                .eq("status", ModelStatusEnum.ACTIVE.getValue())
                .orderBy("priority", false));
    }

    @Override
    public void updateModelMetrics(Long modelId, String healthStatus, int avgLatency, BigDecimal successRate, BigDecimal score) {
        if(Objects.isNull(modelId)) {
            return;
        }
        UpdateChain.of(Model.class)
                .set(Model::getHealthStatus, healthStatus)
                .set(Model::getAvgLatency, avgLatency)
                .set(Model::getSuccessRate, successRate)
                .set(Model::getScore, score)
                .where(Model::getId).eq(modelId)
                .update();
    }
}
