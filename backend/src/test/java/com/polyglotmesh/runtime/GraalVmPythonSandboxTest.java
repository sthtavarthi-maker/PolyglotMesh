package com.polyglotmesh.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.polyglotmesh.config.ExecutionProperties;
import com.polyglotmesh.config.GraalVmProperties;
import com.polyglotmesh.model.ExecutionResult;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Integration tests against the real GraalPy engine: execution, standard input,
 * error reporting, host/file denial and timeout cancellation.
 */
class GraalVmPythonSandboxTest {

    private static GraalVmEngine engine;
    private static GraalVmSandbox sandbox;

    @BeforeAll
    static void setUp() {
        engine = new GraalVmEngine(properties(Duration.ofSeconds(10), 10_000));
        sandbox = new GraalVmSandbox(
                engine, properties(Duration.ofSeconds(10), 10_000), executionProperties());
    }

    @AfterAll
    static void tearDown() {
        sandbox.close();
        engine.close();
    }

    @Test
    void runsHelloWorld() {
        ExecutionResult result =
                sandbox.execute("python", "print('Hello PolyglotMesh')", "");

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("Hello PolyglotMesh\n");
        assertThat(result.error()).isNull();
    }

    @Test
    void evaluatesPythonExpressions() {
        ExecutionResult result = sandbox.execute("python", """
                squares = [n * n for n in range(5)]
                print(squares)
                print(f"{sum(squares)=}")
                """, "");

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("[0, 1, 4, 9, 16]\nsum(squares)=30\n");
    }

    @Test
    void readsStandardInputWithInput() {
        ExecutionResult result = sandbox.execute("python", """
                name = input()
                print(f"Hello, {name}!")
                """, "Ada\n");

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("Hello, Ada!\n");
    }

    @Test
    void readsMultipleInputLines() {
        ExecutionResult result = sandbox.execute("python", """
                first = int(input())
                second = int(input())
                print(first + second)
                """, "2\n40\n");

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("42\n");
    }

    @Test
    void reportsRuntimeErrors() {
        ExecutionResult result = sandbox.execute("python", "print(1 / 0)", "");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("ZeroDivisionError");
    }

    @Test
    void reportsSyntaxErrors() {
        ExecutionResult result = sandbox.execute("python", "def broken(:", "");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).containsIgnoringCase("syntax");
    }

    @Test
    void blocksHostAccess() {
        ExecutionResult result = sandbox.execute("python", """
                try:
                    import java
                    java.lang.System.getProperty('user.name')
                    print('LEAK')
                except Exception:
                    print('blocked')
                """, "");

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("blocked").doesNotContain("LEAK");
    }

    @Test
    void blocksFileAccess() {
        ExecutionResult result = sandbox.execute("python", """
                try:
                    open('C:/Windows/win.ini').read()
                    print('LEAK')
                except Exception:
                    print('blocked')
                """, "");

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("blocked").doesNotContain("LEAK");
    }

    @Test
    void blocksEnvironmentAccess() {
        ExecutionResult result = sandbox.execute("python", """
                import os
                leaked = os.environ.get('PATH') or os.getenv('USERNAME')
                for name in ('posix', 'nt'):
                    try:
                        module = __import__(name)
                        if hasattr(module, 'environ'):
                            leaked = leaked or module.environ.get('PATH')
                    except Exception:
                        pass
                print('LEAK' if leaked else 'blocked')
                """, "");

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("blocked").doesNotContain("LEAK");
    }

    @Test
    void keepsGlobalsBetweenExecutionsIsolated() {
        sandbox.execute("python", "leaked = 'yes'", "");

        ExecutionResult second =
                sandbox.execute("python", "print('leaked' in globals())", "");

        assertThat(second.success()).isTrue();
        assertThat(second.output()).isEqualTo("False\n");
    }

    @Test
    void truncatesLargeOutput() {
        GraalVmSandbox small = new GraalVmSandbox(
                engine, properties(Duration.ofSeconds(10), 50), executionProperties());
        try {
            ExecutionResult result = small.execute("python", "print('x' * 500)", "");

            assertThat(result.success()).isTrue();
            assertThat(result.output()).endsWith("[output truncated]");
            assertThat(result.output().length()).isLessThan(80);
        } finally {
            small.close();
        }
    }

    @Test
    @Timeout(30)
    void cancelsInfiniteLoops() {
        GraalVmSandbox quick = new GraalVmSandbox(
                engine, properties(Duration.ofSeconds(1), 1_000), executionProperties());
        try {
            long startedAt = System.nanoTime();
            ExecutionResult result = quick.execute("python", "while True:\n    pass", "");
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

            assertThat(result.success()).isFalse();
            assertThat(result.error()).contains("timed out");
            assertThat(elapsedMs).isLessThan(10_000);
        } finally {
            quick.close();
        }
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
