package com.polyglotmesh.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.polyglotmesh.config.ExecutionProperties;
import com.polyglotmesh.config.GraalVmProperties;
import com.polyglotmesh.model.ExecutionResult;
import com.polyglotmesh.model.ExecutionStatus;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Integration tests against the real GraalJS engine. They prove that the
 * sandbox executes code, captures output, blocks host access, enforces the
 * timeout and caps output — the behaviour the JavaScript adapter relies on.
 */
class GraalVmSandboxTest {

    private static GraalVmEngine engine;
    private static GraalVmSandbox sandbox;

    @BeforeAll
    static void setUp() {
        engine = new GraalVmEngine(properties(Duration.ofSeconds(3), 10_000));
        sandbox = new GraalVmSandbox(
                engine, properties(Duration.ofSeconds(3), 10_000), executionProperties());
    }

    @AfterAll
    static void tearDown() {
        sandbox.close();
        engine.close();
    }

    @Test
    void runsHelloWorld() {
        ExecutionResult result =
                sandbox.execute("js", "console.log('Hello PolyglotMesh');", "");

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("Hello PolyglotMesh\n");
        assertThat(result.stderr()).isEmpty();
        assertThat(result.error()).isNull();
        assertThat(result.status()).isEqualTo(ExecutionStatus.SUCCESS);
    }

    @Test
    void separatesStandardOutputAndStandardError() {
        ExecutionResult result = sandbox.execute(
                "js",
                "console.log('to stdout'); console.error('to stderr');",
                "");

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("to stdout\n");
        assertThat(result.stderr()).isEqualTo("to stderr\n");
        assertThat(result.error()).isNull();
        assertThat(result.status()).isEqualTo(ExecutionStatus.SUCCESS);
    }

    @Test
    void reportsRuntimeErrors() {
        ExecutionResult result = sandbox.execute("js", "throw new Error('boom');", "");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("boom");
        assertThat(result.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
    }

    @Test
    void doesNotExposeBackendStackTraces() {
        ExecutionResult result = sandbox.execute("js", "throw new Error('boom');", "");

        assertThat(result.error())
                .doesNotContain("com.polyglotmesh")
                .doesNotContain("GraalVmSandbox");
    }

    @Test
    void reportsSyntaxErrors() {
        ExecutionResult result = sandbox.execute("js", "console.log(", "");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).containsIgnoringCase("syntax");
        assertThat(result.status()).isEqualTo(ExecutionStatus.SYNTAX_ERROR);
    }

    @Test
    void blocksHostAccess() {
        ExecutionResult result = sandbox.execute("js", """
                try {
                  Java.type('java.lang.System');
                  console.log('LEAK');
                } catch (error) {
                  console.log('blocked');
                }
                """, "");

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("blocked").doesNotContain("LEAK");
    }

    @Test
    @Timeout(15)
    void cancelsInfiniteLoops() {
        GraalVmSandbox quick = new GraalVmSandbox(
                engine, properties(Duration.ofMillis(300), 1_000), executionProperties());
        try {
            long startedAt = System.nanoTime();
            ExecutionResult result = quick.execute("js", "while (true) {}", "");
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

            assertThat(result.success()).isFalse();
            assertThat(result.error()).contains("timed out");
            assertThat(result.status()).isEqualTo(ExecutionStatus.TIMEOUT);
            assertThat(elapsedMs).isLessThan(5_000);
        } finally {
            quick.close();
        }
    }

    @Test
    void truncatesLargeOutput() {
        GraalVmSandbox small = new GraalVmSandbox(
                engine, properties(Duration.ofSeconds(3), 50), executionProperties());
        try {
            ExecutionResult result =
                    small.execute("js", "console.log('x'.repeat(500));", "");

            assertThat(result.success()).isTrue();
            assertThat(result.output()).endsWith("[output truncated]");
            assertThat(result.output().length()).isLessThan(80);
            assertThat(result.status()).isEqualTo(ExecutionStatus.SUCCESS);
        } finally {
            small.close();
        }
    }

    @Test
    void exposesStandardInputAsLines() {
        ExecutionResult result = sandbox.execute("js", """
                const first = readLine();
                const rest = readAll();
                console.log(first);
                console.log(rest);
                """, "alpha\nbeta\ngamma\n");

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("alpha\nbeta\ngamma\n");
    }

    @Test
    void readLineReturnsNullWhenInputIsExhausted() {
        ExecutionResult result = sandbox.execute("js", "console.log(readLine());", "");

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("null\n");
    }

    @Test
    void keepsGlobalsBetweenExecutionsIsolated() {
        sandbox.execute("js", "globalThis.leaked = 'yes';", "");

        ExecutionResult second =
                sandbox.execute("js", "console.log(typeof globalThis.leaked);", "");

        assertThat(second.output()).isEqualTo("undefined\n");
    }

    @Test
    void returnsPartialOutputWhenTheProgramFails() {
        ExecutionResult result =
                sandbox.execute("js", "console.log('before'); throw new Error('after');", "");

        assertThat(result.success()).isFalse();
        assertThat(result.output()).isEqualTo("before\n");
        assertThat(result.stderr()).isEmpty();
        assertThat(result.error()).contains("after");
        assertThat(result.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
    }

    @Test
    @Timeout(10)
    void rejectsExecutionsAfterShutdown() {
        GraalVmSandbox closed = new GraalVmSandbox(
                engine, properties(Duration.ofSeconds(1), 1_000), executionProperties());
        closed.close();

        ExecutionResult result = closed.execute("js", "console.log('too late');", "");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("shutting down");
        assertThat(result.status()).isEqualTo(ExecutionStatus.CANCELLED);
    }

    private static GraalVmProperties properties(Duration timeout, int maxOutputChars) {
        return new GraalVmProperties(
                true, Duration.ofSeconds(60), timeout, maxOutputChars, 2_000);
    }

    private static ExecutionProperties executionProperties() {
        return new ExecutionProperties(
                true,
                60,
                Duration.ofMinutes(1),
                4,
                Duration.ofSeconds(2),
                Duration.ofSeconds(30),
                Duration.ofSeconds(10),
                Duration.ofHours(1));
    }
}
