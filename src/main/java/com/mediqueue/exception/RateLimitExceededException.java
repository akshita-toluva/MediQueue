package com.mediqueue.exception;

// Thrown when a token bucket is empty. Carries how long the client should wait,
// so the 429 response can include a Retry-After header.
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}