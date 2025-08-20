package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
public class CacheClient {
    StringRedisTemplate stringRedisTemplate;
    ExecutorService cacheThreadPool = Executors.newFixedThreadPool(10);


    public CacheClient(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void setCache(String key, Object data, TimeUnit timeUnit , Long ttl){

        String jsonStr = JSONUtil.toJsonStr(data);
        stringRedisTemplate.opsForValue().set(key,jsonStr,ttl,timeUnit);
    }

    public void setCacheWithLogicalExpire(String key, Object data, TimeUnit timeUnit , Long ttl){
        RedisData redisData = new RedisData();
        redisData.setData(data);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(ttl)));

        String jsonStr = JSONUtil.toJsonStr(redisData);
        stringRedisTemplate.opsForValue().set(key,jsonStr);
    }


    public <ID,R> R getWithPassThrough(String prefix, ID id,Class<R> type, Function<ID,R> func){
        String key = prefix + id ;
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        if(!StringUtils.isEmpty(jsonStr)){
            R data = JSONUtil.toBean(jsonStr, type);
            return data;
        }
        if(jsonStr !=null) return null;
        R data = func.apply(id);
        if(data==null){
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null ;
        }
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(data),RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);
        return data;
    }

    public <ID,R> R getWithLogicalExpire(String prefix, ID id,Class<R> type, Function<ID,R> func ,String lockPrefix,Long ttl,TimeUnit timeUnit){
        String key = prefix + id ;
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        if(StringUtils.isEmpty(jsonStr)){
            return null;
        }
        RedisData redisData = JSONUtil.toBean(jsonStr,RedisData.class);
        JSONObject jsonObject = (JSONObject) redisData.getData();
        R data = JSONUtil.toBean(jsonObject,type);
        LocalDateTime time = redisData.getExpireTime();
        if(LocalDateTime.now().isBefore(time)){
            return data;
        }
        cacheThreadPool.execute(()->{
                String lockKey = lockPrefix + id ;
                boolean flag = tryLock(lockKey);
                if(flag){
                    try {
                        R r  = func.apply(id);
                        RedisData redisData1 = new RedisData();
                        redisData1.setData(r);
                        redisData1.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(ttl)));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        releaseLock(lockKey);
                    }
                }
        });
        return data;



    }

    private boolean tryLock(String key){

        Boolean res = stringRedisTemplate.opsForValue().setIfAbsent(key,Thread.currentThread().getName(),RedisConstants.LOCK_SHOP_TTL,TimeUnit.SECONDS);

        return BooleanUtil.isTrue(res);
    }

    private boolean releaseLock(String key){
        Boolean res = false;
        //检查是否是此线程持有锁
        if(Thread.currentThread().getName().equals(stringRedisTemplate.opsForValue().get(key))){
            res = stringRedisTemplate.delete(key);
        }
        return BooleanUtil.isTrue(res);

    }

}
