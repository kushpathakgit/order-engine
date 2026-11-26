
package com.orderengine.controller;

import com.orderengine.service.RedisTestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedisTestController {

    private final RedisTestService redisTestService;

    public RedisTestController(
            RedisTestService redisTestService) {
        this.redisTestService = redisTestService;
    }

    @GetMapping("/api/redis/test")
    public ResponseEntity<String> testRedis() {
        return ResponseEntity.ok(
                redisTestService.testRedis());
    }
}
