package com.ucbps.common;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "ucbps.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryCounterStore implements CounterStore {
    private record Slot(long count, long expiresAt) {}

    private final ConcurrentHashMap<String, Slot> slots = new ConcurrentHashMap<>();

    @Override
    public long increment(String key, Duration window) {
        long now = System.currentTimeMillis();
        return slots.compute(key, (k, s) -> (s == null || s.expiresAt() <= now)
                ? new Slot(1, now + window.toMillis())
                : new Slot(s.count() + 1, s.expiresAt())).count();
    }
}
