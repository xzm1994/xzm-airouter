package com.example.airouter.controller;

import com.example.airouter.annotation.AuthCheck;
import com.example.airouter.common.BaseResponse;
import com.example.airouter.common.ResultUtils;
import com.example.airouter.exception.ErrorCode;
import com.example.airouter.exception.ThrowUtils;
import com.example.airouter.model.dto.user.UserAddRequest;
import com.example.airouter.model.dto.user.UserLoginRequest;
import com.example.airouter.model.dto.user.UserRegisterRequest;
import com.example.airouter.model.entity.User;
import com.example.airouter.model.vo.user.LoginUserVO;
import com.example.airouter.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @Resource
    private UserService userService;

    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest){
        ThrowUtils.throwIf(Objects.isNull(userRegisterRequest), ErrorCode.PARAMS_ERROR);

        long userId = userService.userRegister(userRegisterRequest.getUserAccount(), userRegisterRequest.getUserPassword(), userRegisterRequest.getCheckPassword());
        return ResultUtils.success(userId);
    }

    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request){
        ThrowUtils.throwIf(Objects.isNull(userLoginRequest), ErrorCode.PARAMS_ERROR);

        LoginUserVO userVO = userService.userLogin(userLoginRequest.getUserAccount(), userLoginRequest.getUserPassword(), request);
        return ResultUtils.success(userVO);
    }

    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request){
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userService.getLoginUserVO(loginUser));
    }

    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request){
        ThrowUtils.throwIf(Objects.isNull(request), ErrorCode.PARAMS_ERROR);
        Boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }


    @PostMapping("/add")
    @AuthCheck("admin")
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest){
        // 必须要admin权限
        return ResultUtils.success(1L);
    }
}
