package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

public class SimpleRedisLock implements ILock {

    private static String prefix = "lock:";
    private String name ;
    private StringRedisTemplate redisTemplate;

    @Override
    public boolean tryLock(Long timeoutSec) {
        return false;
    }

    @Override
    public void unlock() {

    }
}
