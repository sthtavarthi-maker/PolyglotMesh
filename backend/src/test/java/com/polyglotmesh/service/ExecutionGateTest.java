package com.polyglotmesh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.polyglotmesh.config.ExecutionProperties;
import com.polyglotmesh.exception.TooManyRequestsException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Admission control without a Spring context: fixed-window quotas per client,
 * bounded concurrency, and permit release.
 */
class ExecutionGateTest {

    private static final Duration WINDOW = Duration.ofSeconds(60);

    @Test
    void allowsRequestsUpToTheLimitThenRejectsWithRetryAfter() {
        AtomicLong now = new AtomicLong(1_000_000);
        ExecutionGate gate = new ExecutionGate(
                properties(true, 2, WINDOW, 4, Duration.ZERO), now::get);

        gate.acquire("client").close();
        gate.acquire("client").close();

        assertThatThrownBy(() -> gate.acquire("client"))
                .isInstanceOfSatisfying(TooManyRequestsException.class, exception -> {
                    assertThat(exception.getMessage()).contains("Too many");
                    assertThat(exception.retryAfterSeconds()).isEqualTo(60);
                });
    }

    @Test
    void resetsTheQuotaAfterTheWindow() {
        AtomicLong now = new AtomicLong(1_000_000);
        ExecutionGate gate = new ExecutionGate(
                properties(true, 1, WINDOW, 4, Duration.ZERO), now::get);

        gate.acquire("client").close();
        assertThatThrownBy(() -> gate.acquire("client"))
                .isInstanceOf(TooManyRequestsException.class);

        now.addAndGet(WINDOW.toMillis());

        gate.acquire("client").close();
    }

    @Test
    void tracksClientsSeparately() {
        AtomicLong now = new AtomicLong(1_000_000);
        ExecutionGate gate = new ExecutionGate(
                properties(true, 1, WINDOW, 4, Duration.ZERO), now::get);

        gate.acquire("first").close();
        gate.acquire("second").close();

        assertThatThrownBy(() -> gate.acquire("first"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("Too many");
    }

    @Test
    void rateLimitingCanBeDisabled() {
        AtomicLong now = new AtomicLong(1_000_000);
        ExecutionGate gate = new ExecutionGate(
                properties(false, 1, WINDOW, 4, Duration.ZERO), now::get);

        for (int attempt = 0; attempt < 10; attempt++) {
            gate.acquire("client").close();
        }
    }

    @Test
    void rejectsWhenEveryExecutionSlotIsBusy() {
        AtomicLong now = new AtomicLong(1_000_000);
        ExecutionGate gate = new ExecutionGate(
                properties(false, 60, WINDOW, 1, Duration.ofMillis(50)), now::get);

        ExecutionGate.Permit held = gate.acquire("client");
        long startedAt = System.nanoTime();
        assertThatThrownBy(() -> gate.acquire("client"))
                .isInstanceOfSatisfying(TooManyRequestsException.class, exception -> {
                    assertThat(exception.getMessage()).contains("busy");
                    assertThat(exception.retryAfterSeconds()).isEqualTo(1);
                });
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        assertThat(elapsedMs).isGreaterThanOrEqualTo(40);

        held.close();
        gate.acquire("client").close();
    }

    @Test
    void releasingAPermitTwiceDoesNotCreateExtraCapacity() {
        AtomicLong now = new AtomicLong(1_000_000);
        ExecutionGate gate = new ExecutionGate(
                properties(false, 60, WINDOW, 1, Duration.ofMillis(50)), now::get);

        ExecutionGate.Permit permit = gate.acquire("client");
        permit.close();
        permit.close();

        ExecutionGate.Permit held = gate.acquire("client");
        assertThatThrownBy(() -> gate.acquire("client"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("busy");
        held.close();
    }

    private static ExecutionProperties properties(
            boolean rateLimitEnabled,
            int requests,
            Duration window,
            int maxConcurrent,
            Duration acquireTimeout) {
        return new ExecutionProperties(
                rateLimitEnabled,
                requests,
                window,
                maxConcurrent,
                acquireTimeout,
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                Duration.ofHours(1));
    }
}
