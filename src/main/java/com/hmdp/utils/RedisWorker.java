package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisWorker {
    private static final long BEGIN_TIMESTAMP = 1755729721;
    @Resource
    StringRedisTemplate stringRedisTemplate;

    private static final int count_bits =32;

    public long nextId(String prefix){
        LocalDateTime now = LocalDateTime.now();
        long second = now.toEpochSecond(ZoneOffset.UTC);
        long offset = second - BEGIN_TIMESTAMP;
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + prefix +":"+ date);

        long id = offset<<count_bits | count;

        return id;
    }

}
