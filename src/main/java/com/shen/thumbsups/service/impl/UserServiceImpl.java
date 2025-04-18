package com.shen.thumbsups.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shen.thumbsups.constant.UserConstant;
import com.shen.thumbsups.domain.User;
import com.shen.thumbsups.service.UserService;
import com.shen.thumbsups.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
* @author 76453
* @description 针对表【user】的数据库操作Service实现
* @createDate 2025-04-18 10:03:40
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

    @Override
    public User getLoginUser(HttpServletRequest request) {
        User user = (User) request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        return user;
    }
}




