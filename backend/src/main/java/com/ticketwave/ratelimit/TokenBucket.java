package com.ticketwave.ratelimit;

import java.time.Clock;
import java.time.Duration;

/**
 * A continuous-refill token bucket (not a fixed-window counter), so there's
 * no "thundering herd at the window boundary" edge case. Not thread-safe on
 * its own — callers (RateLimiter) are expected to serialize access per key.
 */
public class TokenBucket {

    private final double capacity;
    private final double refillTokensPerMillis;
    private final Clock clock;

    private double availableTokens;
    private long lastRefillMillis;

    public TokenBucket(int capacity, Duration refillPeriod, Clock clock) {
        this.capacity = capacity;
        this.refillTokensPerMillis = capacity / (double) refillPeriod.toMillis();
        this.clock = clock;
        this.availableTokens = capacity;
        this.lastRefillMillis = clock.millis();
    }

    public synchronized boolean tryConsume() {
        refill();
        if (availableTokens >= 1.0) {
            availableTokens -= 1.0;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = clock.millis();
        long elapsedMillis = now - lastRefillMillis;
        if (elapsedMillis > 0) {
            availableTokens = Math.min(capacity, availableTokens + elapsedMillis * refillTokensPerMillis);
            lastRefillMillis = now;
        }
    }
}
