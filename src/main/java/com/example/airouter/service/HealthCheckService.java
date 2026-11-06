package com.example.airouter.service;

/**
 * 健康检查服务
 */
public interface HealthCheckService {

    void checkAllProviders();

    void syncModelMetricsFromRequestLog();
}
