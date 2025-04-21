package com.shen.thumbsups.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shen.thumbsups.common.ErrorCode;
import com.shen.thumbsups.constant.RedisLuaScriptConstant;
import com.shen.thumbsups.constant.ThumbConstant;
import com.shen.thumbsups.domain.Blog;
import com.shen.thumbsups.domain.Thumb;
import com.shen.thumbsups.domain.User;
import com.shen.thumbsups.domain.dto.thumb.DoThumbRequest;
import com.shen.thumbsups.domain.enums.LuaStatusEnum;
import com.shen.thumbsups.exception.BusinessException;
import com.shen.thumbsups.mapper.ThumbMapper;
import com.shen.thumbsups.service.BlogService;
import com.shen.thumbsups.service.ThumbService;
import com.shen.thumbsups.service.UserService;
import com.shen.thumbsups.util.RedisKeyUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;

/**
 * @author 76453
 * @description 针对表【thumb】的数据库操作Service实现
 * @createDate 2025-04-18 10:03:40
 */
@Service("thumbServiceRedis")
@Slf4j
public class ThumbServiceRedisImpl extends ServiceImpl<ThumbMapper, Thumb>
        implements ThumbService {

    @Resource
    private UserService userService;

    @Resource
    private BlogService blogService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        Long blogId = doThumbRequest.getBlogId();
        String timeSlice = getTimeSlice();
        // Redis Key
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(timeSlice);
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUser.getId());

        log.info("当前创建临时点赞key : {}", tempThumbKey);

        // 执行Lua脚本
        long result = redisTemplate.execute(
                RedisLuaScriptConstant.THUMB_SCRIPT,
                Arrays.asList(tempThumbKey, userThumbKey),
                loginUser.getId(),
                blogId
        );

        if (LuaStatusEnum.FAIL.getValue() == result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户已点赞");
        }

        return LuaStatusEnum.SUCCESS.getValue() == result;
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        Long blogId = doThumbRequest.getBlogId();

        String timeSlice = getTimeSlice();
        // Redis Key
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(timeSlice);
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUser.getId());
        log.info("当前创建临时取消点赞key : {}", tempThumbKey);

        long result = redisTemplate.execute(
                RedisLuaScriptConstant.UNTHUMB_SCRIPT,
                Arrays.asList(tempThumbKey, userThumbKey),
                loginUser.getId(),
                blogId
        );

        if (LuaStatusEnum.FAIL.getValue() == result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户未点赞");
        }

        return LuaStatusEnum.SUCCESS.getValue() == result;
    }

    private String getTimeSlice() {
        DateTime now = DateUtil.date();
        /*
         * 获取到当前时间最近的整数秒，避免重复点赞
         * 例： 11:20:23 -> 11:20:20
         */
        return DateUtil.format(now, "HH:mm:") + (DateUtil.second(now) / 10) * 10;
    }

    @Override
    public Boolean hasThumb(Long userId, Long blogId) {
        return redisTemplate.opsForHash().hasKey(ThumbConstant.USER_THUMB_KEY_PREFIX + userId, blogId.toString());
    }
}




