package com.example.airouter.task;

import com.example.airouter.service.HealthCheckService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 健康检查定时任务
 */
@Component
@Slf4j
public class HealthCheckTask {

    @Resource
    private HealthCheckService healthCheckService;

    @Scheduled(fixedRate = 30000)
    public void executeHealthCheck() {
        log.info("Health check task started");
        healthCheckService.checkAllProviders();
        log.info("Health check task finished");
    }
}
