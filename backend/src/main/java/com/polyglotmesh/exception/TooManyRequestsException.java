package com.polyglotmesh.exception;

/**
 * Raised when admission control refuses a request: either the client exceeded
 * its rate limit or no execution slot became free in time.
 *
 * <p>The API maps this to {@code 429 Too Many Requests} and sends a
 * {@code Retry-After} header. The message is safe to show to clients; it never
 * contains internal details.</p>
 */
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    /** Seconds the client should wait before retrying (at least one). */
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
