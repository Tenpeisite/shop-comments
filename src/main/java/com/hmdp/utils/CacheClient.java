package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @author 朱焕杰
 * @version 1.0
 * @date 2023/1/12 15:25
 */
@Component
@Slf4j
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                          Long time, TimeUnit unit, Function<ID, R> dbFallback) {
        String key = keyPrefix + id;
        //1.从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            //2.1.存在
            return JSONUtil.toBean(json, type);
        }
        //2.2.不存在
        //判断是否为空值
        if (json != null) {
            //不为null，则必为空
            return null;
        }
        //3.查询数据库
        R r = dbFallback.apply(id);
        if (r == null) {
            //3.1.不存在，缓存空值
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
        } else {
            //3.2.存在，缓存数据
            this.set(key, r, time, unit);
        }
        return r;
    }

    public <R, ID> R queryWithLogicalExpire(String prefix, ID id, String lockPre, Class<R> type,
                                            Long time, TimeUnit unit, Function<ID, R> dbFallback) {
        //1.从redis查询商铺缓存
        String key = prefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isBlank(json)) {
            //未命中，直接返回空
            return null;
        }
        //3.命中，判断是否过期
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            //3.1未过期,直接返回店铺信息
            return r;
        }
        //3.2.已过期，缓存重建
        //3.3.获取锁
        String lockKey = lockPre + id;
        boolean flag = tryLock(lockKey);
        if (flag) {
            //3.4.获取成功
            //4再次检查redis缓存是否过期，做double check
            json = stringRedisTemplate.opsForValue().get(key);
            //4.1.判断是否存在
            if (StrUtil.isBlank(json)) {
                //未命中，直接返回空
                return null;
            }
            //4.2.命中，判断是否过期
            redisData = JSONUtil.toBean(json, RedisData.class);
            r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
            if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
                //4.3.未过期,直接返回店铺信息
                return r;
            }
            //4.4过期，返回旧数据
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                //5.重建缓存
                try {
                    R r1 = dbFallback.apply(id);
                    this.setWithLogicExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        //7.获取失败,返回旧数据
        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    //获取锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}
