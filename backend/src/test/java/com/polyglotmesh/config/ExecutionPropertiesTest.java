package com.polyglotmesh.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * The compile budget and the Java run budget must stay independent: on the
 * development host a cold compile dominates, while a warm child JVM is fast.
 * These tests pin the safe defaults and the configured values.
 */
class ExecutionPropertiesTest {

    @Test
    void appliesSafeDefaultsWhenValuesAreMissing() {
        ExecutionProperties properties = new ExecutionProperties(
                null, 0, null, 0, null, null, null, null);

        assertThat(properties.rateLimitingEnabled()).isTrue();
        assertThat(properties.rateLimitRequests()).isEqualTo(60);
        assertThat(properties.rateLimitWindowMillis()).isEqualTo(60_000);
        assertThat(properties.maxConcurrentExecutions()).isEqualTo(4);
        assertThat(properties.acquireTimeoutMillis()).isEqualTo(2_000);
        assertThat(properties.compileTimeoutNanos())
                .isEqualTo(Duration.ofSeconds(20).toNanos());
        assertThat(properties.javaExecutionTimeoutMillis()).isEqualTo(10_000);
        assertThat(properties.staleWorkDirAgeMillis())
                .isEqualTo(Duration.ofHours(1).toMillis());
    }

    @Test
    void keepsCompileAndJavaExecutionBudgetsSeparate() {
        ExecutionProperties properties = new ExecutionProperties(
                true,
                5,
                Duration.ofSeconds(30),
                2,
                Duration.ofMillis(500),
                Duration.ofSeconds(25),
                Duration.ofSeconds(7),
                Duration.ofMinutes(30));

        assertThat(properties.compileTimeoutNanos())
                .isEqualTo(Duration.ofSeconds(25).toNanos());
        assertThat(properties.javaExecutionTimeoutMillis()).isEqualTo(7_000);
        assertThat(properties.compileTimeoutNanos())
                .isGreaterThan(properties.javaExecutionTimeoutMillis() * 1_000_000);
    }
}
