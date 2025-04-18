package com.shen.thumbsups.service;

import com.shen.thumbsups.domain.Thumb;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shen.thumbsups.domain.dto.thumb.DoThumbRequest;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author 76453
* @description 针对表【thumb】的数据库操作Service
* @createDate 2025-04-18 10:03:40
*/
public interface ThumbService extends IService<Thumb> {

    /**
     * 点赞
     * @param doThumbRequest 博客id
     * @param request        获取登录用户
     * @return               是否成功点赞
     */
    Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);

    /**
     * 取消点赞
     * @param doThumbRequest 博客id
     * @param request        获取登录用户
     * @return               是否成功取消点赞
     */
    Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);

}
