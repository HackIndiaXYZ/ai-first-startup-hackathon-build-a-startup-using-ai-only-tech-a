package in.smarttech.dukaandost.security;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** In-memory sliding-window counter keyed by an arbitrary string such as a client IP. */
public class SlidingWindowLimiter {

    private static final int EVICTION_THRESHOLD = 10_000;

    private final int maxEvents;
    private final long windowMillis;
    private final Clock clock;
    private final Map<String, Deque<Long>> events = new HashMap<>();

    public SlidingWindowLimiter(int maxEvents, Duration window, Clock clock) {
        if (maxEvents < 1 || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Limiter needs a positive limit and window.");
        }
        this.maxEvents = maxEvents;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    /** Seconds until another event is allowed, or 0 when the key is under its limit. */
    public synchronized long retryAfterSeconds(String key) {
        long now = clock.millis();
        Deque<Long> window = prune(key, now);
        if (window == null || window.size() < maxEvents) {
            return 0;
        }
        long waitMillis = window.peekFirst() + windowMillis - now;
        return Math.max(1, (waitMillis + 999) / 1000);
    }

    /** Records an event if the key is under its limit. Returns false when limited. */
    public synchronized boolean tryAcquire(String key) {
        if (retryAfterSeconds(key) > 0) {
            return false;
        }
        record(key);
        return true;
    }

    public synchronized void record(String key) {
        long now = clock.millis();
        if (events.size() >= EVICTION_THRESHOLD) {
            events.keySet().removeIf(candidate -> prune(candidate, now) == null);
        }
        events.computeIfAbsent(key, ignored -> new ArrayDeque<>()).addLast(now);
    }

    public synchronized void reset(String key) {
        events.remove(key);
    }

    private Deque<Long> prune(String key, long now) {
        Deque<Long> window = events.get(key);
        if (window == null) {
            return null;
        }
        while (!window.isEmpty() && now - window.peekFirst() >= windowMillis) {
            window.removeFirst();
        }
        if (window.isEmpty()) {
            events.remove(key);
            return null;
        }
        return window;
    }
}
