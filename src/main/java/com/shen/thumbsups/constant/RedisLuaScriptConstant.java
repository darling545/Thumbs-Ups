package com.shen.thumbsups.constant;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

public class RedisLuaScriptConstant {

    /**
     * 用户点赞操作Lua脚本（保证原子性）
     *
     * KEYS参数说明：
     * [1] tempThumbKey -> 临时点赞计数器Hash结构键名（存储userId:blogId与点赞次数的映射）
     * [2] userThumbKey -> 用户点赞记录Hash结构键名（存储blogId与点赞状态的映射）
     *
     * ARGV参数说明：
     * [1] userId -> 执行点赞操作的用户ID
     * [2] blogId -> 被点赞的博客ID
     *
     * 返回值说明：
     * 1  -> 点赞成功
     * -1 -> 重复点赞（已存在点赞记录）
     */
    public static final RedisScript<Long> THUMB_SCRIPT = new DefaultRedisScript<>("""
            local tempThumbKey = KEYS[1]
            local userThumbKey = KEYS[2]
            local userId = ARGV[1]
            local blogId = ARGV[2]
            
            if redis.call('HEXISTS', userThumbKey, blogId) == 1 then
                return -1
            end
            
            local hashKey = userId .. ':' .. blogId
            local oldNumber = tonumber(redis.call('HGET', tempThumbKey, hashKey) or 0)
            
            local newNumber = oldNumber + 1
            redis.call('HSET', tempThumbKey, hashKey, newNumber)
            redis.call('HSET', userThumbKey, blogId, 1)
            
            return 1
            """, Long.class);


    /**
     * 用户取消点赞操作Lua脚本（保证原子性）
     *
     * KEYS参数说明：
     * [1] tempThumbKey -> 临时点赞计数器Hash结构键名（存储userId:blogId与点赞次数的映射）
     * [2] userThumbKey -> 用户点赞记录Hash结构键名（存储blogId与点赞状态的映射）
     *
     * ARGV参数说明：
     * [1] userId -> 执行点赞操作的用户ID
     * [2] blogId -> 被点赞的博客ID
     *
     * 返回值说明：
     * 1  -> 取消成功
     * -1 -> 未点赞
     */
    public static final RedisScript<Long> UNTHUMB_SCRIPT = new DefaultRedisScript<>("""
            local tempThumbKey = KEYS[1]
            local userThumbKey = KEYS[2]
            local userId = ARGV[1]
            local blogId = ARGV[2]
            
            if redis.call('HEXISTS', userThumbKey, blogId) ~= 1 then
                return -1
            end
            
            local hashKey = userId .. ':' .. blogId
            local oldNumber = tonumber(redis.call('HGET', tempThumbKey, hashKey) or 0)
            
            local newNumber = oldNumber - 1
            
            redis.call('HSET', tempThumbKey, hashKey, newNumber)
            redis.call('HDEL', userThumbKey, blogId)
            
            return 1
            """, Long.class);
}
