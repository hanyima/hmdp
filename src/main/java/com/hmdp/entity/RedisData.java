package com.hmdp.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData {
    public Object data ;

    public LocalDateTime expireTime ;
}
