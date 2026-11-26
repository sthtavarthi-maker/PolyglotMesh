package com.polyglotmesh.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Admission-control and cleanup limits for the execution endpoint.
 *
 * <p>They are configuration because the right values depend on the deployment:
 * a single-user development machine can allow more parallel work than a shared
 * server. Defaults are deliberately small and fail closed with HTTP 429 rather
 * than letting requests pile up.</p>
 *
 * @param rateLimitEnabled        whether per-client request throttling is active
 * @param rateLimitRequests       valid execution requests allowed per client and window
 * @param rateLimitWindow         length of the fixed rate-limit window
 * @param maxConcurrentExecutions execution slots available at the same time
 * @param acquireTimeout          how long a request waits for a free slot before 429
 * @param compileTimeout          upper bound for compiling one Java program
 * @param javaExecutionTimeout    upper bound for running one compiled Java program in its
 *                                child JVM; kept separate from compilation because a cold
 *                                JDK toolchain needs far longer to compile than to run
 * @param staleWorkDirAge         age after which abandoned temp directories are swept
 */
@ConfigurationProperties(prefix = "polyglotmesh.execution")
public record ExecutionProperties(
        Boolean rateLimitEnabled,
        int rateLimitRequests,
        Duration rateLimitWindow,
        int maxConcurrentExecutions,
        Duration acquireTimeout,
        Duration compileTimeout,
        Duration javaExecutionTimeout,
        Duration staleWorkDirAge) {

    private static final int DEFAULT_RATE_LIMIT_REQUESTS = 60;
    private static final Duration DEFAULT_RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    private static final int DEFAULT_MAX_CONCURRENT_EXECUTIONS = 4;
    private static final Duration DEFAULT_ACQUIRE_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_COMPILE_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration DEFAULT_JAVA_EXECUTION_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_STALE_WORK_DIR_AGE = Duration.ofHours(1);

    public ExecutionProperties {
        if (rateLimitEnabled == null) {
            rateLimitEnabled = Boolean.TRUE;
        }
        if (rateLimitRequests <= 0) {
            rateLimitRequests = DEFAULT_RATE_LIMIT_REQUESTS;
        }
        if (rateLimitWindow == null || rateLimitWindow.isZero() || rateLimitWindow.isNegative()) {
            rateLimitWindow = DEFAULT_RATE_LIMIT_WINDOW;
        }
        if (maxConcurrentExecutions <= 0) {
            maxConcurrentExecutions = DEFAULT_MAX_CONCURRENT_EXECUTIONS;
        }
        if (acquireTimeout == null || acquireTimeout.isNegative()) {
            acquireTimeout = DEFAULT_ACQUIRE_TIMEOUT;
        }
        if (compileTimeout == null || compileTimeout.isZero() || compileTimeout.isNegative()) {
            compileTimeout = DEFAULT_COMPILE_TIMEOUT;
        }
        if (javaExecutionTimeout == null
                || javaExecutionTimeout.isZero()
                || javaExecutionTimeout.isNegative()) {
            javaExecutionTimeout = DEFAULT_JAVA_EXECUTION_TIMEOUT;
        }
        if (staleWorkDirAge == null || staleWorkDirAge.isZero() || staleWorkDirAge.isNegative()) {
            staleWorkDirAge = DEFAULT_STALE_WORK_DIR_AGE;
        }
    }

    /** Primitive view of {@link #rateLimitEnabled()} with the safe default applied. */
    public boolean rateLimitingEnabled() {
        return Boolean.TRUE.equals(rateLimitEnabled);
    }

    public long rateLimitWindowMillis() {
        return rateLimitWindow.toMillis();
    }

    public long acquireTimeoutMillis() {
        return acquireTimeout.toMillis();
    }

    public long compileTimeoutNanos() {
        return compileTimeout.toNanos();
    }

    public long javaExecutionTimeoutMillis() {
        return javaExecutionTimeout.toMillis();
    }

    public long staleWorkDirAgeMillis() {
        return staleWorkDirAge.toMillis();
    }
}
