package com.ticketwave.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketTest {

    @Test
    void tryConsume_upToCapacity_succeedsThenFailsWithNoTimePassing() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        TestClock clock = new TestClock(now);
        TokenBucket bucket = new TokenBucket(3, Duration.ofSeconds(60), clock);

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    void tryConsume_afterFullWindowElapses_refillsToFullCapacity() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        TestClock clock = new TestClock(now);
        TokenBucket bucket = new TokenBucket(2, Duration.ofSeconds(60), clock);

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();

        clock.advance(Duration.ofSeconds(60));

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    void tryConsume_afterPartialWindowElapses_refillsProportionally() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        TestClock clock = new TestClock(now);
        TokenBucket bucket = new TokenBucket(4, Duration.ofSeconds(40), clock);

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();

        // Half the window (20s of 40s) at capacity 4 refills ~2 tokens.
        clock.advance(Duration.ofSeconds(20));

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }

    /**
     * A mutable Clock the test can advance on demand, since java.time has no
     * built-in adjustable fixed clock.
     */
    private static final class TestClock extends Clock {
        private Instant instant;

        private TestClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
