
package com.orderengine.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisTestService {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisTestService(
            RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String testRedis() {

        redisTemplate.opsForValue()
                .set("order-engine:test", "Redis connected");

        Object value = redisTemplate.opsForValue()
                .get("order-engine:test");

        return value.toString();
    }
}
