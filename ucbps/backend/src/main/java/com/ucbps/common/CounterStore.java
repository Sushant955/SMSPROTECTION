package com.ucbps.common;

import java.time.Duration;

/** Fixed-window counter used for rate limiting. In-memory by default, Redis when ucbps.redis.enabled=true. */
public interface CounterStore {
    long increment(String key, Duration window);
}
