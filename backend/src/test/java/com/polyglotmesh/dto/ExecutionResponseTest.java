package com.polyglotmesh.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.polyglotmesh.model.ExecutionResult;
import com.polyglotmesh.model.ExecutionStatus;
import com.polyglotmesh.model.Language;
import org.junit.jupiter.api.Test;

/**
 * The public response must expose stdout, stderr, the diagnostic and the
 * classification without duplicating or dropping any of them.
 */
class ExecutionResponseTest {

    @Test
    void mapsEveryFieldOfASuccessfulResult() {
        ExecutionResult result = ExecutionResult.success("out\n", "err\n", 25);

        ExecutionResponse response = ExecutionResponse.from(Language.JAVASCRIPT, result);

        assertThat(response.success()).isTrue();
        assertThat(response.language()).isEqualTo("javascript");
        assertThat(response.output()).isEqualTo("out\n");
        assertThat(response.stderr()).isEqualTo("err\n");
        assertThat(response.error()).isNull();
        assertThat(response.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(response.executionTimeMs()).isEqualTo(25);
    }

    @Test
    void mapsAFailedResultWithItsDiagnosticAndStatus() {
        ExecutionResult result = ExecutionResult.failure(
                ExecutionStatus.SYNTAX_ERROR,
                "partial\n",
                "warning\n",
                "SyntaxError: invalid syntax",
                9);

        ExecutionResponse response = ExecutionResponse.from(Language.PYTHON, result);

        assertThat(response.success()).isFalse();
        assertThat(response.language()).isEqualTo("python");
        assertThat(response.output()).isEqualTo("partial\n");
        assertThat(response.stderr()).isEqualTo("warning\n");
        assertThat(response.error()).isEqualTo("SyntaxError: invalid syntax");
        assertThat(response.status()).isEqualTo(ExecutionStatus.SYNTAX_ERROR);
        assertThat(response.executionTimeMs()).isEqualTo(9);
    }

    @Test
    void keepsJavaLanguageIdentity() {
        ExecutionResponse response = ExecutionResponse.from(
                Language.JAVA, ExecutionResult.failure("missingMethod", 4));

        assertThat(response.language()).isEqualTo("java");
        assertThat(response.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
        assertThat(response.stderr()).isEmpty();
    }
}
