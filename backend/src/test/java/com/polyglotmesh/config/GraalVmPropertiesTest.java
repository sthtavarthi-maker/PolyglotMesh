package com.polyglotmesh.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * GraalVM setup (context creation and stdin installation) is measured
 * separately from guest execution because a cold GraalPy context is far more
 * expensive than any normal program.
 */
class GraalVmPropertiesTest {

    @Test
    void defaultsSetupWithoutChargingItToExecution() {
        GraalVmProperties properties =
                new GraalVmProperties(true, null, Duration.ofSeconds(5), 10_000, 2_000);

        assertThat(properties.setupTimeoutMillis()).isEqualTo(60_000);
        assertThat(properties.timeoutMillis()).isEqualTo(5_000);
    }

    @Test
    void keepsConfiguredSetupAndExecutionBudgets() {
        GraalVmProperties properties = new GraalVmProperties(
                true, Duration.ofSeconds(45), Duration.ofMillis(750), 10_000, 2_000);

        assertThat(properties.setupTimeoutMillis()).isEqualTo(45_000);
        assertThat(properties.timeoutMillis()).isEqualTo(750);
    }
}
