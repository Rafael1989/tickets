package com.ticketwave.ratelimit;

import com.ticketwave.config.RateLimitProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One token bucket per key (client IP), lazily created on first use. Buckets
 * are never evicted — for this project's scale that's an acceptable tradeoff
 * (a long-running process would slowly accumulate one small object per
 * distinct client IP it has ever seen); a production deployment facing
 * genuinely unbounded/hostile traffic would want an eviction policy or an
 * external store (e.g. Redis) instead of this in-memory map.
 */
@Component
public class RateLimiter {

    private final RateLimitProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    /**
     * A single constructor only — Spring's implicit constructor-autowiring
     * relies on there being exactly one, with no @Autowired needed. Clock is
     * a real injected dependency (see RateLimitConfig's Clock bean) rather
     * than a second overloaded constructor, precisely so tests can supply a
     * fixed/adjustable Clock without any ambiguity for Spring to resolve.
     */
    public RateLimiter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public boolean tryConsume(String key) {
        TokenBucket bucket = buckets.computeIfAbsent(key, ignored -> newBucket());
        return bucket.tryConsume();
    }

    private TokenBucket newBucket() {
        return new TokenBucket(properties.requestsPerWindow(), Duration.ofSeconds(properties.windowSeconds()), clock);
    }
}
