package in.smarttech.dukaandost.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SlidingWindowLimiterTest {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void blocksAfterLimitAndRecoversAfterWindow() {
        MutableClock clock = new MutableClock();
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(2, Duration.ofSeconds(60), clock);

        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("b"));

        clock.now = clock.now.plusSeconds(61);
        assertTrue(limiter.tryAcquire("a"));
    }

    @Test
    void resetClearsKey() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(1, Duration.ofSeconds(60), new MutableClock());
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        limiter.reset("a");
        assertEquals(0, limiter.retryAfterSeconds("a"));
        assertTrue(limiter.tryAcquire("a"));
    }
}
