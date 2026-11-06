package com.example.airouter.service;

import com.example.airouter.model.dto.requestlog.RequestLogDTO;
import com.example.airouter.model.entity.RequestLog;
import com.mybatisflex.core.service.IService;

import java.util.List;

public interface RequestLogService extends IService<RequestLog> {

    /**
     * 记录请求日志
     *
     * @param logDTO 日志记录对象
     */
    void logRequest(RequestLogDTO logDTO);

    /**
     *  记录请求日志
     * @param userId
     * @param apiKeyId
     * @param modelName
     * @param promptTokens
     * @param completionTokens
     * @param totalTokens
     * @param duration
     * @param status
     * @param errorMessage
     */
    void logRequest(Long userId, Long apiKeyId, String modelName,
                    Integer promptTokens, Integer completionTokens, Integer totalTokens,
                    Integer duration, String status, String errorMessage);

    /**
     * 查询用户的请求日志
     * @param userId 用户id
     * @param limit 条数
     * @return 日志信息
     */
    List<RequestLog> listUserLogs(Long userId, Integer limit);

    /**
     * 统计用户的token 消耗
     * @param userId 用户id
     * @return tokn数
     */
    Long countUserTokens(Long userId);
}
