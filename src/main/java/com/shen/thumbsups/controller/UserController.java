package com.shen.thumbsups.controller;

import com.shen.thumbsups.common.BaseResponse;
import com.shen.thumbsups.common.ResultUtils;
import com.shen.thumbsups.constant.UserConstant;
import com.shen.thumbsups.domain.User;
import com.shen.thumbsups.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("user")
public class UserController {

    @Resource
    private UserService userService;

    @GetMapping("/login")
    public BaseResponse<User> login(@RequestParam("userId") long userId, HttpServletRequest request) {
        User user = userService.getById(userId);
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user);
        return ResultUtils.success(user);
    }

    @GetMapping("/getLoginUser")
    public BaseResponse<User> getLoginUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(loginUser);
    }
}
