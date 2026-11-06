package com.example.airouter.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.example.airouter.mapper.RequestLogMapper;
import com.example.airouter.model.entity.Model;
import com.example.airouter.model.entity.ModelProvider;
import com.example.airouter.model.enums.HealthStatusEnum;
import com.example.airouter.model.enums.ProviderStatusEnum;
import com.example.airouter.service.HealthCheckService;
import com.example.airouter.service.ModelProviderService;
import com.example.airouter.service.ModelService;
import com.example.airouter.service.RequestLogService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.stringtemplate.v4.ST;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 健康检查实现类
 */
@Service
@Slf4j
public class HealthCheckServiceImpl implements HealthCheckService {
    @Resource
    private ModelProviderService modelProviderService;

    @Resource
    private ModelService modelService;

    @Resource
    private RequestLogMapper requestLogMapper;

    /**
     * 储存最近的健康检查结果（滑动窗口）
     */
    private final ConcurrentHashMap<Long, List<Boolean>> healthHistoryMap = new ConcurrentHashMap<>();

    /**
     * 健康检查超时时间（毫秒）
     */
    private static final int HEALTH_CHECK_TIMEOUT = 10000;

    /**
     * 健康检查历史记录最大数量
     */
    private static final int MAX_HISTORY_SIZE = 100;

    /**
     * 统计请求日志的时间范围（小时）
     */
    private static final int STATS_HOURS = 24;

    /**
     * 成本权重（用于计算综合得分）
     */
    private static final double COST_WEIGHT = 0.3;

    /**
     * 延迟权重（用于计算综合得分）
     */
    private static final double LATENCY_WEIGHT = 0.3;

    /**
     * 成功率权重（用于计算综合得分）
     */
    private static final double SUCCESS_RATE_WEIGHT = 0.2;

    /**
     * 优先级权重（用于计算综合得分）
     */
    private static final double PRIORITY_WEIGHT = 0.2;

    @Override
    public void checkAllProviders() {
        // 获取所有启用的提供者
        List<ModelProvider> modelProviders = modelProviderService.list();
        if (CollectionUtils.isEmpty(modelProviders)) {
            log.warn("No model providers found");
            return;
        }
        log.info("开始健康检查,共{}个提供商", modelProviders.size());
        // 并行检查所有提供者
        modelProviders.parallelStream().filter(modelProvider -> ProviderStatusEnum.ACTIVE.getValue().equals(modelProvider.getStatus()))
                .forEach(this::checkProviderHealth);
        // 从请求日志同步模型指标
        syncModelMetricsFromRequestLog();
        log.info("健康检查完成");


    }

    /**
     *
     */
    @Override
    public void syncModelMetricsFromRequestLog() {
        log.info("开始从请求日志同步模型指标");

        // 获取所有启用的模型
        List<Model> activeModels = modelService.getActiveModels();
        if (CollectionUtils.isEmpty(activeModels)) {
            log.warn("No models found");
            return;
        }

        // 计算统计时间范围
        LocalDateTime startTime = LocalDateTime.now().minusHours(STATS_HOURS);

        // 使用数据库聚合函数批量查询所有模型的统计数据
        List<ModelStats> modelStatsList = queryModelStatsFromDb(startTime);
        // 转化为Map 便于查找
        Map<Long, ModelStats> statsMap = modelStatsList.stream()
                .collect(Collectors.toMap(
                        ModelStats::getModelId,
                        Function.identity(),
                        (oldVal, newVal) -> oldVal // 重复key，保留旧的；改成 newVal 就是覆盖
                ));
        // 计算归一化参数（用于综合得分计算）
        NormalizationParams normParams = calculateNormalizationParams(activeModels, statsMap);

        // 更新每个模型的指标
        for (Model model : activeModels) {

            ModelStats modelStats = statsMap.getOrDefault(model.getId(), new ModelStats());
            BigDecimal score = calculateScore(model, modelStats, normParams);
            String healthStatus = determineHealthStatus(modelStats.getSuccessRate());
            modelService.updateModelMetrics(model.getId(), healthStatus, modelStats.getAvgLatency(), modelStats.getSuccessRate(), score);
            log.info("模型{}指标更新：延迟={}ms，成功率={}%，得分={}", model.getModelKey(), modelStats.getAvgLatency(), modelStats.getSuccessRate(), score);
        }
        // 同步更新提供者的汇总指标（基于模型统计数据）
        syncProviderMetricsFromModelStats(activeModels, statsMap);

        log.info("模型指标同步完成");
    }

    /**
     * 根据模型统计数据同步提供者指标
     * @param activeModels
     * @param statsMap
     */
    private void syncProviderMetricsFromModelStats(List<Model> activeModels, Map<Long, ModelStats> statsMap) {
        //按提供者分组
        Map<Long, List<Model>> provideModels = activeModels.stream().collect(Collectors.groupingBy(Model::getProviderId));
        for (Map.Entry<Long, List<Model>> entry : provideModels.entrySet()) {
            Long providerId = entry.getKey();
            List<Model> providerModelList = entry.getValue();
            // 计算改提供者所有模型的平均延迟和成功率
            int totalLatency = 0;
            int latencyCount = 0;
            double totalSuccess = 0;
            int successRateCount = 0;

            for (Model model : providerModelList) {
                ModelStats modelStats = statsMap.get(model.getId());
                if (modelStats != null && modelStats.avgLatency > 0) {
                    totalLatency += modelStats.avgLatency;
                    latencyCount++;
                }
                if (modelStats != null && modelStats.totalRequests > 0) {
                    totalSuccess += modelStats.totalRequests;
                    successRateCount++;
                }
            }

            int avgLatency =latencyCount > 0 ? totalLatency / latencyCount : 0;
            BigDecimal avgSuccessRate = successRateCount > 0
                    ? BigDecimal.valueOf(totalSuccess / successRateCount).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(100);

            // 根据成功率确定健康状态
            String healthStatus = determineHealthStatus(avgSuccessRate);
            modelProviderService.updateHealthStatus(providerId, healthStatus, avgLatency, avgSuccessRate);
            log.debug("提供者 {} 指标更新: 延迟={}ms, 成功率={}%, 健康状态={}",
                    providerId, avgLatency, avgSuccessRate, healthStatus);
        }

    }

    /**
     * 计算模型综合得分(越低越好)
     * 综合得分的计算是自动路由策略的核心。我们需要将成本、延迟、成功率、优先级等多个维度的数据归一化后加权求和。
     * 得分计算规则：
     * 成本权重 30%：考虑模型的价格因素，模型价格越接近最高价。采用正向归一化 (value - min) / range。归一化结果越接近 1（得分高，坏）；价格越低，结果越接近 0（得分低，好）。
     * 延迟权重 30%：考虑模型的响应速度。采用正向归一化。采用反向计算 1.0 - (成功率/100)。响应时间越长，归一化结果越接近 1（得分高，坏）。
     * 成功率权重 20%：考虑模型的稳定性。采用反向计算 1.0 - (成功率/100)。
     * 优先级权重 20%：考虑管理员设置的优先级。
     * 权重可以根据业务需求调整，比如对成本敏感的业务可以提高成本权重。
     *
     * @param model
     * @param modelStats
     * @param normParams
     * @return
     */
    private BigDecimal calculateScore(Model model, ModelStats modelStats, NormalizationParams normParams) {
        // 1. 成本得分（）规划到0-1
        BigDecimal modelCost = model.getInputPrice().add(model.getOutputPrice());
        double costScore = normalize(modelCost.intValue(), normParams.minCost.intValue(), normParams.maxCost.intValue());

        // 2. 延迟得分规划
        int avgLatency = modelStats.getAvgLatency() > 0 ? modelStats.getAvgLatency() : 5000;
        double latencyScore = normalize(avgLatency, normParams.minLatency, normParams.maxLatency);

        // 3.成功率得分
        double successRateScore = 1.0 - (modelStats.successRate.doubleValue() / 100.0);

        // 4.优先级得分
        double priorityScore = 1.0 - normalize(model.getPriority(), normParams.minPriority, normParams.maxPriority);

        // 5.综合得分
        double score = costScore * COST_WEIGHT + latencyScore * LATENCY_WEIGHT
                + successRateScore * SUCCESS_RATE_WEIGHT
                + priorityScore * PRIORITY_WEIGHT;
        return BigDecimal.valueOf(score).setScale(4,RoundingMode.HALF_UP);
    }

    /**
     * 归一化数值到0-1范围
     *
     * @param currentValue
     * @param minValue
     * @param maxValue
     * @return
     */
    private double normalize(int currentValue, int minValue, int maxValue) {
        int range = maxValue - minValue;
        if (range == 0) {
            return 0.0;
        }
        return (double)(currentValue - minValue) / range;
    }

    /**
     * 计算归一化参数
     *
     * @param activeModels
     * @param statsMap
     * @return
     */
    private NormalizationParams calculateNormalizationParams(List<Model> activeModels, Map<Long, ModelStats> statsMap) {
        NormalizationParams params = new NormalizationParams();

        //成本范围
        params.minCost = activeModels.stream().map(m -> m.getInputPrice().add(m.getOutputPrice())).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        params.maxCost = activeModels.stream().map(m -> m.getInputPrice().add(m.getOutputPrice())).min(BigDecimal::compareTo).orElse(BigDecimal.ONE);

        // 延迟范围
        params.minLatency = statsMap.values().stream().mapToInt(ModelStats::getAvgLatency).filter(l -> l > 0).min().orElse(0);
        params.maxLatency = statsMap.values().stream().mapToInt(ModelStats::getAvgLatency).filter(l -> l > 0).max().orElse(10000);

        // 优先级范围
        params.minPriority = activeModels.stream().mapToInt(Model::getPriority).min().orElse(0);
        params.maxPriority = activeModels.stream().mapToInt(Model::getPriority).max().orElse(100);
        return params;
    }

    /**
     * 使用数据库聚合函数批量查询所有模型的统计数据
     *
     * @param startTime
     * @return
     */
    private List<ModelStats> queryModelStatsFromDb(LocalDateTime startTime) {
        // 使用 SQL 聚合函数一次性查询所有模型的统计数据
        // SELECT modelId,
        //        AVG(CASE WHEN status = 'success' THEN duration ELSE NULL END) as avgLatency,
        //        COUNT(*) as totalRequests,
        //        SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) * 100.0 / COUNT(*) as successRate
        // FROM request_log
        // WHERE createTime >= ? AND modelId IS NOT NULL
        // GROUP BY modelId
        QueryWrapper queryWrapper = QueryWrapper.create().select("modelId", "AVG(CASE WHEN status = 'success' THEN duration ELSE NULL END) as avgLatency",
                        "COUNT(*) as totalRequests",
                        "IFNULL(SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0), 100) as successRate")
                .from("request_log").ge("createTime", startTime).isNotNull("modelId").groupBy("modelId");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) (List<?>) requestLogMapper.selectObjectByQueryAs(queryWrapper, Map.class);
        List<ModelStats> modelStatsList = new ArrayList<>();
        for (Map<String, Object> result : results) {
            ModelStats modelStats = new ModelStats();
            modelStats.modelId = result.get("modelId") != null ? ((Number) result.get("modelId")).longValue() : null;
            modelStats.avgLatency = result.get("avgLatency") != null ? ((Number) result.get("avgLatency")).intValue() : 0;
            modelStats.totalRequests = result.get("totalRequests") != null ? ((Number) result.get("totalRequests")).intValue() : 0;
            modelStats.successRate = result.get("successRate") != null ? BigDecimal.valueOf(((Number) result.get("successRate")).doubleValue()).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(100);
            modelStatsList.add(modelStats);
        }
        return modelStatsList;
    }

    /**
     * 检查提供商是否可用
     *
     * @param modelProvider
     */
    private boolean checkProviderHealth(ModelProvider modelProvider) {
        if (Objects.isNull(modelProvider)) {
            return false;
        }
        try {
            long startTime = System.currentTimeMillis();
            boolean isHealthy = sendHealthCheckRequest(modelProvider);
            long endTime = System.currentTimeMillis();
            int latency = (int) (endTime - startTime);

            // 记录健康检查结果
            recordHealthCheckResult(modelProvider.getId(), isHealthy);

            // 计算成功率
            BigDecimal successRate = caculateSuccessRate(modelProvider.getId());

            // 确定健康状态
            String healthStatus = determineHealthStatus(successRate);

            // 更新提供者的健康信息
            modelProviderService.updateHealthStatus(modelProvider.getId(), healthStatus, latency, successRate);
            log.info("提供者{}健康检查完成：健康={}，延迟={}ms，成功率={}%", modelProvider.getDisplayName(), isHealthy, latency, successRate);
            return isHealthy;
        } catch (Exception e) {
            log.error("提供者{}健康检查失败", modelProvider.getDisplayName(), e);
            // 记录失败
            recordHealthCheckResult(modelProvider.getId(), false);
            // 计算成功率
            BigDecimal successRate = caculateSuccessRate(modelProvider.getId());
            // 更新提供者的健康信息
            modelProviderService.updateHealthStatus(modelProvider.getId(), HealthStatusEnum.UNHEALTHY.getValue(), 0, successRate);
            return false;
        }
    }

    /**
     * 根据成功率确定提供者的健康状态，定义了三个等一：健康、降级、不健康
     * >=80 healthy; 50<=rate<80: degraded; <50: unhralthy
     *
     * @param successRate
     * @return
     */
    private String determineHealthStatus(BigDecimal successRate) {
        double rate = successRate.doubleValue();
        if (rate >= 80.0) {
            return HealthStatusEnum.HEALTHY.getValue();
        } else if (rate >= 50.0) {
            return HealthStatusEnum.DEGRADED.getValue();
        } else {
            return HealthStatusEnum.UNHEALTHY.getValue();
        }
    }

    /**
     * 计算成功率
     *
     * @param providerId
     * @return
     */
    private BigDecimal caculateSuccessRate(Long providerId) {
        List<Boolean> totalNums = healthHistoryMap.get(providerId);
        if (CollectionUtils.isEmpty(totalNums)) {
            return BigDecimal.valueOf(100.00);
        }
        int successNums = totalNums.stream().filter(d -> d == true).toList().size();
        double rate = (successNums * 100.0) / totalNums.size();
        return BigDecimal.valueOf(rate).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 记录健康检查结果
     *
     * @param providerId
     * @param isHealthy
     */
    private void recordHealthCheckResult(Long providerId, boolean isHealthy) {
        List<Boolean> history = healthHistoryMap.computeIfAbsent(providerId, k -> new ArrayList<>());
        history.add(isHealthy);
        // 只保留最近100此
        if (history.size() > MAX_HISTORY_SIZE) {
            history.removeFirst();
        }
    }

    /**
     * 发送健康检查请求
     *
     * @param modelProvider
     * @return
     */
    private boolean sendHealthCheckRequest(ModelProvider modelProvider) {
        try {
            String baseUrl = modelProvider.getBaseUrl();
            String apiKey = modelProvider.getApiKey();
            // 构筑一个简单的测试请求（检查模型列表接口）
            String url = baseUrl + "/models";
            HttpResponse httpResponse = HttpRequest.get(url).header("Authorization", "Bearer " + apiKey).timeout(HEALTH_CHECK_TIMEOUT).execute();
            return httpResponse.getStatus() == 200 || httpResponse.getStatus() == 401;
        } catch (Exception e) {
            log.warn("提供者：{}健康检查失败：{}", modelProvider.getDisplayName(), e.getMessage());
            return false;
        }
    }

    /**
     * 模型统计数据内部类
     */
    @Data
    private static class ModelStats {
        Long modelId;
        int avgLatency = 0;
        BigDecimal successRate = BigDecimal.valueOf(100);
        int totalRequests = 0;
    }

    /**
     * 归一化参数内部类
     */
    @Data
    private static class NormalizationParams {
        BigDecimal minCost = BigDecimal.ZERO;
        BigDecimal maxCost = BigDecimal.ONE;
        int minLatency = 0;
        int maxLatency = 10000;
        int minPriority = 0;
        int maxPriority = 100;
    }
}
