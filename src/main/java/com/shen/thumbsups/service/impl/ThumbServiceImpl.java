package com.shen.thumbsups.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shen.thumbsups.common.ErrorCode;
import com.shen.thumbsups.constant.ThumbConstant;
import com.shen.thumbsups.domain.Blog;
import com.shen.thumbsups.domain.Thumb;
import com.shen.thumbsups.domain.User;
import com.shen.thumbsups.domain.dto.thumb.DoThumbRequest;
import com.shen.thumbsups.exception.BusinessException;
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
 * @author 76453
 * @description 针对表【thumb】的数据库操作Service实现
 * @createDate 2025-04-18 10:03:40
 */
@Service
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
                    redisTemplate.opsForHash().put(ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId().toString(), blogId.toString(), thumb.getId());
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
                Long thumbId = Long.valueOf(redisTemplate.opsForHash().get(ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId().toString(), blogId.toString()).toString());
                if (thumbId == null) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户已取消点赞");
                }
                boolean update = blogService.lambdaUpdate()
                        .eq(Blog::getId, blogId)
                        .setSql("thumbCount = thumbCount - 1")
                        .update();
                // 更新成功后在执行
                boolean success = update && this.removeById(thumbId);
                if (success) {
                    redisTemplate.opsForHash().delete(ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId(), blogId.toString());
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
            return redisTemplate.opsForHash().hasKey(ThumbConstant.USER_THUMB_KEY_PREFIX + userId, blogId.toString());
        }
    }
}




