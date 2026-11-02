package com.example.airouter.controller;

import cn.hutool.core.bean.BeanUtil;
import com.example.airouter.common.BaseResponse;
import com.example.airouter.common.DeleteRequest;
import com.example.airouter.common.ResultUtils;
import com.example.airouter.model.dto.apikey.ApiKeyCreateRequest;
import com.example.airouter.model.entity.ApiKey;
import com.example.airouter.model.entity.User;
import com.example.airouter.model.vo.apikey.ApiKeyVO;
import com.example.airouter.service.ApiKeyService;
import com.example.airouter.service.UserService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/key")
@Slf4j
public class ApiKeyController {

    @Resource
    private ApiKeyService  apiKeyService;

    @Resource
    private UserService userService;

    @PostMapping("/create")
    public BaseResponse<ApiKeyVO> create(@RequestBody ApiKeyCreateRequest apiKeyCreateRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        ApiKey apiKey = apiKeyService.createApiKey(apiKeyCreateRequest.getKeyName(), loginUser);
        // 转换为VO
        ApiKeyVO apiKeyVO = BeanUtil.copyProperties(apiKey, ApiKeyVO.class);
        return ResultUtils.success(apiKeyVO);
    }

    @GetMapping("/list/my")
    public BaseResponse<Page<ApiKeyVO>> listMyApiKeys( @RequestParam(defaultValue = "1") long pageNum,
                                                       @RequestParam(defaultValue = "10") long pageSize,
                                                       HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        QueryWrapper queryWrapper = QueryWrapper.create().eq(ApiKey::getUserId, loginUser.getId()).eq(ApiKey::getIsDelete, 0).orderBy("createTime", false);
        Page<ApiKey> apiKeyPage = apiKeyService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 转换为 VO（列表中部分隐藏 Key 值）
        Page<ApiKeyVO> result = new Page<>(pageNum, pageSize, apiKeyPage.getTotalRow());
        List<ApiKeyVO> apiKeyVOS = apiKeyPage.getRecords().stream().map(apiKey -> {
            ApiKeyVO vo = BeanUtil.copyProperties(apiKey, ApiKeyVO.class);
            // 隐藏部分 Key 值（只显示前8位和后4位）
            if (vo.getKeyValue() != null && vo.getKeyValue().length() > 12) {
                String key = vo.getKeyValue();
                vo.setKeyValue(key.substring(0, 8) + "****" + key.substring(key.length() - 4));
            }
            return vo;
        }).toList();
        result.setRecords(apiKeyVOS);
        return ResultUtils.success(result);
    }

    @PostMapping("/revoke")
    public BaseResponse<Boolean> revoke(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        boolean result = apiKeyService.revokeApiKey(deleteRequest.getId(), loginUser.getId());
        return ResultUtils.success(result);
    }
}
