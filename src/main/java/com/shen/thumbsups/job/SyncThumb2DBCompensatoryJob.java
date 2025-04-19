package com.shen.thumbsups.job;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import com.shen.thumbsups.constant.ThumbConstant;
import com.shen.thumbsups.util.RedisKeyUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 补偿策略
 */
@Component
@Slf4j
public class SyncThumb2DBCompensatoryJob {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private SyncThumb2DBJob syncThumb2DBJob;


    @Scheduled(cron = "0 0 2 * * *")
    public void run() {
        log.info("开始补偿点赞数据到数据库");
        Set<String> thumbsKeys = redisTemplate.keys(RedisKeyUtil.getTempThumbKey("") + "*");
        Set<String> needHandleDataSet = new HashSet<>();
        thumbsKeys.stream().filter(ObjUtil::isNotNull).forEach(thumbsKey -> needHandleDataSet.add(
                thumbsKey.replace(ThumbConstant.TEMP_THUMB_KEY_PREFIX.formatted(""),"")
        ));

        if (CollUtil.isEmpty(needHandleDataSet)) {
            log.info("暂无需要补偿的数据");
            return;
        }
        for (String date : needHandleDataSet) {
            syncThumb2DBJob.syncThumb2DBDate(date);
        }
        log.info("补偿点赞数据到数据库完成");
    }
}
