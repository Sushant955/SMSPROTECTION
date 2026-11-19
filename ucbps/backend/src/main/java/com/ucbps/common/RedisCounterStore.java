package com.ucbps.common;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "ucbps.redis.enabled", havingValue = "true")
public class RedisCounterStore implements CounterStore {
    private final StringRedisTemplate redis;

    public RedisCounterStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long increment(String key, Duration window) {
        Long v = redis.opsForValue().increment(key);
        if (v != null && v == 1L) {
            redis.expire(key, window);
        }
        return v == null ? 0 : v;
    }
}
