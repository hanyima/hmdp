package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import com.sun.tools.javac.util.List;
import org.springframework.core.io.PathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private static final String NAME_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString();
    private String name ;
    private StringRedisTemplate redisTemplate;
    private static DefaultRedisScript script;
    static {
        script = new DefaultRedisScript();
        script.setLocation(new PathResource("unlock.lua"));
    }

    public SimpleRedisLock(StringRedisTemplate redisTemplate, String name) {
        this.redisTemplate = redisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(Long timeoutSec) {

        Boolean flag =  redisTemplate.opsForValue().setIfAbsent(NAME_PREFIX+name,ID_PREFIX+Thread.currentThread().getId(),timeoutSec, TimeUnit.SECONDS);

        return BooleanUtil.isTrue(flag);

    }

    @Override
    public void unlock() {
        redisTemplate.execute(script, List.of(NAME_PREFIX+name), ID_PREFIX+Thread.currentThread().getId());

    }
}
