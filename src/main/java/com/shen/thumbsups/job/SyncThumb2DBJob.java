package com.shen.thumbsups.job;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.StrPool;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shen.thumbsups.domain.Thumb;
import com.shen.thumbsups.domain.enums.ThumbTypeEnum;
import com.shen.thumbsups.mapper.BlogMapper;
import com.shen.thumbsups.service.ThumbService;
import com.shen.thumbsups.util.RedisKeyUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 定时将Redis中的临时点赞数据同步到数据库中
 */
@Component
@Slf4j
public class SyncThumb2DBJob {

    @Resource
    private ThumbService thumbService;

    @Resource
    private BlogMapper blogMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Scheduled(initialDelay = 10000, fixedDelay = 10000)
    public void run() {
        log.info("开始同步点赞数据到数据库");
        DateTime nowDate = DateUtil.date();
        String date = DateUtil.format(nowDate, "HH:mm:") + (DateUtil.second(nowDate) / 10 - 1) * 10;
        log.info("开始同步点赞数据到数据库，当前key：{}", date);
        syncThumb2DBDate(date);
        log.info("同步点赞数据到数据库完成");
    }

    /**
     * 同步指定日期的临时点赞数据到数据库
     *
     * @param date 需要同步的日期字符串，格式应符合业务约定（如yyyyMMdd）
     *             Redis临时数据键会根据该日期参数生成
     */
    public void syncThumb2DBDate(String date) {
        // 获取Redis中当日的临时点赞数据
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(date);
        Map<Object, Object> allTempThumbMap = redisTemplate.opsForHash().entries(tempThumbKey);
        boolean thumbMapEmpty = CollUtil.isEmpty(allTempThumbMap);

        // 处理点赞数据同步逻辑
        Map<Long, Long> blogThumbCountMap = new HashMap<>();
        if (thumbMapEmpty) {
            return;
        }

        // 构建批量插入的点赞记录和删除条件
        ArrayList<Thumb> thumbs = new ArrayList<>();
        LambdaQueryWrapper<Thumb> wrapper = new LambdaQueryWrapper<>();
        boolean needRemove = false;

        // 遍历所有临时记录进行分类处理
        for (Object userIdBlogIdObj : allTempThumbMap.keySet()) {
            String userIdBlogId = (String) userIdBlogIdObj;
            String[] userIdBlogIdArr = userIdBlogId.split(StrPool.COLON);
            Long userId = Long.valueOf(userIdBlogIdArr[0]);
            Long blogId = Long.valueOf(userIdBlogIdArr[1]);

            // 解析操作类型并处理
            Integer thumbType = Integer.valueOf(allTempThumbMap.get(userIdBlogId).toString());
            if (thumbType == ThumbTypeEnum.INCR.getValue()) {
                // 构造新增点赞实体
                Thumb thumb = new Thumb();
                thumb.setBlogId(blogId);
                thumb.setUserId(userId);
                thumbs.add(thumb);
            } else if (thumbType == ThumbTypeEnum.DECR.getValue()) {
                // 构建删除条件（或逻辑）
                needRemove = true;
                wrapper.or().eq(Thumb::getUserId, userId).eq(Thumb::getBlogId, blogId);
            } else {
                // 处理异常数据
                if (thumbType == ThumbTypeEnum.NONE.getValue()) {
                    log.warn("数据异常: {}", userId + "," + blogId + "," + thumbType);
                }
                continue;
            }

            // 累计每个博客的点赞变化量
            blogThumbCountMap.put(blogId, blogThumbCountMap.getOrDefault(blogId, 0L) + thumbType);
        }

        // 批量持久化操作
        thumbService.saveBatch(thumbs);
        if (needRemove) {
            thumbService.remove(wrapper);
        }

        // 更新博客点赞总数
        if (!blogThumbCountMap.isEmpty()) {
            blogMapper.batchUpdateThumbsCount(blogThumbCountMap);
        }

        // 异步清理Redis临时数据
        Thread.startVirtualThread(() -> {
            redisTemplate.delete(tempThumbKey);
        });
    }

}
