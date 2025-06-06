package com.shen.thumbsups.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shen.thumbsups.common.ErrorCode;
import com.shen.thumbsups.constant.ThumbConstant;
import com.shen.thumbsups.domain.Blog;
import com.shen.thumbsups.domain.Thumb;
import com.shen.thumbsups.domain.User;
import com.shen.thumbsups.domain.dto.thumb.DoThumbRequest;
import com.shen.thumbsups.exception.BusinessException;
import com.shen.thumbsups.manager.CacheManager;
import com.shen.thumbsups.mapper.ThumbMapper;
import com.shen.thumbsups.service.BlogService;
import com.shen.thumbsups.service.ThumbService;
import com.shen.thumbsups.service.UserService;
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
import java.util.Date;

/**
 * @author shenguang
 * @description 针对表【thumb】的数据库操作Service实现
 * @createDate 2025-04-18 10:03:40
 */
@Service("thumbService")
@Slf4j
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, Thumb>
        implements ThumbService {

    @Resource
    private UserService userService;

    @Resource
    private BlogService blogService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private CacheManager cacheManager;

    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        // 加锁
        synchronized (loginUser.getId().toString().intern()) {
            return transactionTemplate.execute(status -> {
                Long blogId = doThumbRequest.getBlogId();
                // 超过一个月查询数据库，否则查询redis
                boolean exists = this.hasThumb(loginUser.getId(), blogId);
                if (exists) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户已点赞");
                }
                boolean update = blogService.lambdaUpdate()
                        .eq(Blog::getId, blogId)
                        .setSql("thumbCount = thumbCount + 1")
                        .update();
                Thumb thumb = new Thumb();
                thumb.setUserId(loginUser.getId());
                thumb.setBlogId(blogId);
                // 更新成功后在执行
                boolean success = update && this.save(thumb);
                if (success) {
                    // TODO 设置过期时间（一个月内发布的文章为热点数据，进行存入redis）
                    String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId().toString();
                    String fieldKey = blogId.toString();
                    Long realThumbId = thumb.getId();
                    redisTemplate.opsForHash().put(hashKey, fieldKey, realThumbId);
                    cacheManager.putIfPresent(hashKey, fieldKey, realThumbId);
                }
                return success;
            });
        }
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        // 加锁
        synchronized (loginUser.getId().toString().intern()) {
            return transactionTemplate.execute(status -> {
                Long blogId = doThumbRequest.getBlogId();
                Object thumbIdObj = cacheManager.get(ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId(), blogId.toString());
                if (thumbIdObj == null || thumbIdObj.equals(ThumbConstant.UN_THUMB_CONSTANT)) {
                    throw new RuntimeException("用户未点赞");
                }
                boolean update = blogService.lambdaUpdate()
                        .eq(Blog::getId, blogId)
                        .setSql("thumbCount = thumbCount - 1")
                        .update();
                // 更新成功后在执行
                boolean success = update && this.removeById((Long)thumbIdObj);
                // 点赞记录从 Redis 删除
                if (success) {
                    String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId();
                    String fieldKey = blogId.toString();
                    redisTemplate.opsForHash().delete(hashKey, fieldKey);
                    cacheManager.putIfPresent(hashKey, fieldKey, ThumbConstant.UN_THUMB_CONSTANT);
                }
                return success;
            });
        }
    }

    @Override
    public Boolean hasThumb(Long userId, Long blogId) {
        // 查询发布时间
        Blog blog = blogService.getById(blogId);
        if (blog == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,"文章不存在");
        }
        // 判断当前时间是否已经超过发布文章时间一个月
        Date publishDate = blog.getCreateTime();
        Instant instant = publishDate.toInstant();
        LocalDateTime publishTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        LocalDateTime now = LocalDateTime.now();

        // 计算时间差
        long daysBetween = ChronoUnit.DAYS.between(publishTime, now);
        boolean isOverOneMonth = daysBetween > 30;
        if (isOverOneMonth) {
            // 超过一个月查询数据库
            return this.lambdaQuery()
                    .eq(Thumb::getBlogId, blogId)
                    .eq(Thumb::getUserId, userId)
                    .exists();
        } else {
            // 没有超过一个月查询redis
            Object object = cacheManager.get(ThumbConstant.USER_THUMB_KEY_PREFIX + userId.toString(), blogId.toString());
            if (object == null) {
                return false;
            }
            Long thumbId = Long.valueOf(object.toString());
            return !thumbId.equals(ThumbConstant.UN_THUMB_CONSTANT);
        }
    }
}




