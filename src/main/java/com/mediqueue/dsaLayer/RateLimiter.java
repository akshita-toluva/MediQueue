package com.mediqueue.dsaLayer;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RateLimiter {

    // INVARIANT: idle-expiry must be >= the longest refill period we use.
    // An idle bucket would be full again by then anyway, so evicting it changes nothing.
    private static final Duration IDLE_EXPIRY = Duration.ofMinutes(15);
    private static final long MAX_KEYS = 100_000;   // hard cap on memory under attack

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(IDLE_EXPIRY)
            .maximumSize(MAX_KEYS)
            .build();

    /** Result of one attempt: allowed, or rejected with seconds until a token is back. */
    public record Decision(boolean allowed, long retryAfterSeconds) {}

    /**
     * Try to take 1 token from the bucket for `key`, creating the bucket on first use.
     * Thread-safe: Caffeine's get() creates at most one bucket per key even under
     * concurrent requests, and Bucket4j's tryConsume is atomic (lock-free CAS).
     */
    public Decision tryConsume(String key, int capacity, Duration refillPeriod) {
        Bucket bucket = buckets.get(key, k -> newBucket(capacity, refillPeriod));

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return new Decision(true, 0);
        }
        // Round UP to whole seconds (never tell a client "retry in 0s"), minimum 1.
        long nanos = probe.getNanosToWaitForRefill();
        long seconds = Math.max(1, (nanos + 999_999_999L) / 1_000_000_000L);
        return new Decision(false, seconds);
    }

    private Bucket newBucket(int capacity, Duration refillPeriod) {
        // refillGreedy: tokens trickle back continuously (5/min ≈ 1 every 12s),
        // instead of all 5 appearing at once at the end of the minute (refillIntervally).
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, refillPeriod)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}