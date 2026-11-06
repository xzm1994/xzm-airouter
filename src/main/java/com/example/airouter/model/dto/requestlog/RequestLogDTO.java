package com.example.airouter.model.dto.requestlog;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Builder
@Data
public class RequestLogDTO {

    private String traceId;

    private Long userId;

    private Long apiKeyId;
    /**
     * 实际调用的模型ID
     */
    private Long modelId;

    private String requestModel;

    private String requestType;

    private String source;

    private int duration;

    private String status;
    /**
     * 输入Token数
     */
    private Integer promptTokens;

    /**
     * 输出Token数
     */
    private Integer completionTokens;

    /**
     * 总Token数
     */
    private Integer totalTokens;

    /**
     * 本次请求费用（元）
     */
    private BigDecimal cost;

    private String errorMessage;

    private String errorCode;

    private String routingStrategy;

    private Boolean isFallback;

    private String clientIp;

    private String userAgent;
}
