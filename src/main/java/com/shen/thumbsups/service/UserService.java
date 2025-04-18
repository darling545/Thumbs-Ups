package com.shen.thumbsups.service;

import com.shen.thumbsups.domain.User;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author 76453
* @description 针对表【user】的数据库操作Service
* @createDate 2025-04-18 10:03:40
*/
public interface UserService extends IService<User> {


    User getLoginUser(HttpServletRequest request);
}
