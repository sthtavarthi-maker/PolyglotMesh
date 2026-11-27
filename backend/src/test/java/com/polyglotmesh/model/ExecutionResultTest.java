package com.polyglotmesh.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The internal result keeps stdout and stderr separate and always carries an
 * explicit outcome classification, so the API layer cannot invent one.
 */
class ExecutionResultTest {

    @Test
    void successfulResultHasSuccessStatusAndNoDiagnostic() {
        ExecutionResult result = ExecutionResult.success("hello\n", "warning\n", 12);

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("hello\n");
        assertThat(result.stderr()).isEqualTo("warning\n");
        assertThat(result.error()).isNull();
        assertThat(result.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(result.executionTimeMs()).isEqualTo(12);
    }

    @Test
    void successDefaultsStderrToEmpty() {
        ExecutionResult result = ExecutionResult.success("hello\n", 5);

        assertThat(result.stderr()).isEmpty();
        assertThat(result.status()).isEqualTo(ExecutionStatus.SUCCESS);
    }

    @Test
    void failureKeepsAnExplicitClassification() {
        ExecutionResult result = ExecutionResult.failure(
                ExecutionStatus.COMPILE_ERROR, "", "", "line 3: ';' expected", 7);

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo("line 3: ';' expected");
        assertThat(result.status()).isEqualTo(ExecutionStatus.COMPILE_ERROR);
    }

    @Test
    void genericFailureDefaultsToRuntimeError() {
        ExecutionResult result = ExecutionResult.failure("boom", 3);

        assertThat(result.success()).isFalse();
        assertThat(result.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    void failureWithPartialOutputDefaultsToRuntimeError() {
        ExecutionResult result = ExecutionResult.failure("before\n", "boom", 4);

        assertThat(result.output()).isEqualTo("before\n");
        assertThat(result.stderr()).isEmpty();
        assertThat(result.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
    }

    @Test
    void normalisesNullStreamsToEmptyStrings() {
        ExecutionResult result = new ExecutionResult(
                true, null, null, null, ExecutionStatus.SUCCESS, 1);

        assertThat(result.output()).isEmpty();
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    void rejectsNegativeExecutionTime() {
        assertThatThrownBy(() -> ExecutionResult.success("hello", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("executionTimeMs");
    }

    @Test
    void rejectsASuccessfulResultWithAFailureStatus() {
        assertThatThrownBy(() -> new ExecutionResult(
                        true, "hello", "", null, ExecutionStatus.TIMEOUT, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SUCCESS");
    }

    @Test
    void rejectsAFailedResultWithSuccessStatus() {
        assertThatThrownBy(() -> new ExecutionResult(
                        false, "", "", "boom", ExecutionStatus.SUCCESS, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SUCCESS");
    }
}
