package com.example.airouter.adapter;

import com.example.airouter.exception.BusinessException;
import com.example.airouter.exception.ErrorCode;
import jakarta.annotation.Resource;
import jodd.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 模型适配器工厂
 * 根据提供者名称返回对应的模型适配器
 */
@Slf4j
@Component
public class ModelAdapterFactory {
    @Resource
    private List<ModelAdapter>  modelAdapters;

    public ModelAdapter getAdapter(String providerName) {
        if (StringUtil.isEmpty(providerName)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "提供者名称不能为空");
        }
        for (ModelAdapter adapter : modelAdapters) {
            if (adapter.supports(providerName)) {
                log.debug("为提供者 {} 选择适配器: {}", providerName, adapter.getClass().getSimpleName());
                return adapter;
            }
        }
        log.warn("未找到支持提供者 {} 的适配器，将使用 OpenAI 兼容适配器", providerName);
        // 默认使用 OpenAI 兼容适配器（因为很多模型都兼容 OpenAI 格式）
        return modelAdapters.stream()
                .filter(adapter -> adapter instanceof OpenAIAdapter)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR, "未找到可用的模型适配器"));
    }
}
