package com.example.airouter.service.impl;

import com.example.airouter.mapper.ModelProviderMapper;
import com.example.airouter.model.entity.ModelProvider;
import com.example.airouter.service.ModelProviderService;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class ModelProviderServiceImpl extends ServiceImpl<ModelProviderMapper, ModelProvider> implements ModelProviderService {
    @Override
    public void updateHealthStatus(Long providerId, String healthStatus, int avgLatency, BigDecimal avgSuccessRate) {
        ModelProvider modelProvider = new ModelProvider();
        modelProvider.setId(providerId);
        modelProvider.setHealthStatus(healthStatus);
        modelProvider.setAvgLatency(avgLatency);
        modelProvider.setSuccessRate(avgSuccessRate);
        this.updateById(modelProvider);
    }
}
