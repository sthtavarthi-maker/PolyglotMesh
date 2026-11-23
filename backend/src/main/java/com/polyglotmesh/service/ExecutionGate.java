package com.polyglotmesh.service;

import com.polyglotmesh.config.ExecutionProperties;
import com.polyglotmesh.exception.TooManyRequestsException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Admission control in front of the execution endpoint.
 *
 * <p>Two limits protect the backend: a fixed-window request quota per client
 * and a bounded number of concurrent executions. Requests that exceed either
 * limit fail fast with {@link TooManyRequestsException} instead of queueing
 * without bound. Both limits are in-memory and per instance; a multi-instance
 * deployment needs a shared store or an edge proxy for global enforcement.</p>
 *
 * <p>The client key is the caller's remote address. Forwarded headers are not
 * trusted here, because a direct client can forge them; behind a trusted proxy,
 * keying should move to the proxy or be derived from a verified header.</p>
 */
@Component
public class ExecutionGate {

    private static final int MAX_TRACKED_CLIENTS = 10_000;
    private static final int MAX_CLIENT_KEY_LENGTH = 64;
    private static final String UNKNOWN_CLIENT = "unknown";

    private final ExecutionProperties properties;
    private final LongSupplier clock;
    private final Semaphore slots;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong lastSweepMillis = new AtomicLong();

    @Autowired
    public ExecutionGate(ExecutionProperties properties) {
        this(properties, System::currentTimeMillis);
    }

    /** Package-private constructor so tests can drive the rate-limit clock. */
    ExecutionGate(ExecutionProperties properties, LongSupplier clock) {
        this.properties = properties;
        this.clock = clock;
        this.slots = new Semaphore(properties.maxConcurrentExecutions(), true);
    }

    /**
     * Reserves an execution slot for one request.
     *
     * <p>The returned permit must be closed; the controller uses
     * try-with-resources so the slot is released even when execution throws.
     * The rate-limit check runs first, then the slot wait.</p>
     *
     * @param clientKey caller identity, normally {@code HttpServletRequest#getRemoteAddr()}
     * @throws TooManyRequestsException when the client is over quota or no slot
     *                                  becomes free within the configured wait
     */
    public Permit acquire(String clientKey) {
        long now = clock.getAsLong();
        if (properties.rateLimitingEnabled() && !withinRateLimit(normalize(clientKey), now)) {
            throw new TooManyRequestsException(
                    "Too many execution requests. Try again shortly.", retryAfterSeconds());
        }

        boolean acquired;
        try {
            acquired = slots.tryAcquire(properties.acquireTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new TooManyRequestsException(
                    "Execution was interrupted while waiting for a slot.", 1);
        }
        if (!acquired) {
            throw new TooManyRequestsException(
                    "The execution service is busy. Try again shortly.", 1);
        }
        return new Permit(slots);
    }

    private boolean withinRateLimit(String clientKey, long now) {
        sweepIfNeeded(now);
        Window window = windows.computeIfAbsent(clientKey, ignored -> new Window(now));
        synchronized (window) {
            if (now - window.startMillis >= properties.rateLimitWindowMillis()) {
                window.startMillis = now;
                window.count = 0;
            }
            if (window.count >= properties.rateLimitRequests()) {
                return false;
            }
            window.count++;
            return true;
        }
    }

    /**
     * Keeps the client map bounded. Expired windows are dropped only once the
     * map grows past the cap, and at most once per window, so normal traffic
     * pays nothing.
     */
    private void sweepIfNeeded(long now) {
        if (windows.size() <= MAX_TRACKED_CLIENTS) {
            return;
        }
        long windowMillis = properties.rateLimitWindowMillis();
        long previousSweep = lastSweepMillis.get();
        if (now - previousSweep < windowMillis || !lastSweepMillis.compareAndSet(previousSweep, now)) {
            return;
        }
        windows.entrySet().removeIf(entry -> now - entry.getValue().startMillis >= windowMillis * 2);
    }

    private long retryAfterSeconds() {
        return Math.max(1, (properties.rateLimitWindowMillis() + 999) / 1000);
    }

    private static String normalize(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            return UNKNOWN_CLIENT;
        }
        String trimmed = clientKey.trim();
        return trimmed.length() <= MAX_CLIENT_KEY_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_CLIENT_KEY_LENGTH);
    }

    /** A held execution slot; closing it more than once is harmless. */
    public static final class Permit implements AutoCloseable {

        private final Semaphore slots;
        private final AtomicBoolean released = new AtomicBoolean();

        private Permit(Semaphore slots) {
            this.slots = slots;
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                slots.release();
            }
        }
    }

    private static final class Window {

        private long startMillis;
        private int count;

        private Window(long startMillis) {
            this.startMillis = startMillis;
        }
    }
}
