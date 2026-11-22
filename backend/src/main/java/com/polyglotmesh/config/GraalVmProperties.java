package com.polyglotmesh.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limits applied to every execution, whether it runs on the GraalVM engine or
 * on the JDK toolchain.
 *
 * <p>They are configuration, not constants, because the safe timeout depends on
 * the machine: a laptop running the interpreter needs more headroom than a
 * dedicated build agent. Defaults are deliberately conservative.</p>
 *
 * @param enabled         master switch for the GraalVM engine; Java execution
 *                        uses the JDK toolchain independently
 * @param executionTimeout wall-clock limit for a single execution
 * @param maxOutputChars  cap on returned stdout/stderr
 * @param maxErrorChars   cap on returned error text
 */
@ConfigurationProperties(prefix = "polyglotmesh.graalvm")
public record GraalVmProperties(
        boolean enabled,
        Duration executionTimeout,
        int maxOutputChars,
        int maxErrorChars) {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    private static final int DEFAULT_MAX_OUTPUT_CHARS = 100_000;
    private static final int DEFAULT_MAX_ERROR_CHARS = 20_000;

    public GraalVmProperties {
        if (executionTimeout == null) {
            executionTimeout = DEFAULT_TIMEOUT;
        }
        if (executionTimeout.isZero() || executionTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "polyglotmesh.graalvm.execution-timeout must be positive");
        }
        if (maxOutputChars <= 0) {
            maxOutputChars = DEFAULT_MAX_OUTPUT_CHARS;
        }
        if (maxErrorChars <= 0) {
            maxErrorChars = DEFAULT_MAX_ERROR_CHARS;
        }
    }

    public long timeoutMillis() {
        return executionTimeout.toMillis();
    }
}
