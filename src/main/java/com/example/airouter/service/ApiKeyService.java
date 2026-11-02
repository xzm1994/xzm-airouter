package com.example.airouter.service;

import com.example.airouter.model.entity.ApiKey;
import com.example.airouter.model.entity.User;
import com.mybatisflex.core.service.IService;

import java.util.List;

public interface ApiKeyService extends IService<ApiKey> {

    /**
     * 创建API KEY
     * @param keyName
     * @param loginUser
     * @return
     */
    ApiKey createApiKey(String keyName, User loginUser);


    /**
     * 获取用户的api key 列表
     * @param userId
     * @return
     */
    List<ApiKey> listUserApiKeys(Long userId);

    /**
     * 撤销 API key
     * @param id
     * @param userId
     * @return
     */
    boolean revokeApiKey(Long id, Long userId);

    /**
     * 根据keyvalue值查询apikey
     * @param keyValue
     * @return
     */
    ApiKey getByKeyValue(String keyValue);

    /**
     * 更新 API Key 的使用统计
     *
     * @param apiKeyId API Key ID
     * @param tokens 本次消耗的Token数
     */
    void updateUsageStats(Long apiKeyId, Integer tokens);
}
