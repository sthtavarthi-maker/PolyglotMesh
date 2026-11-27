package com.polyglotmesh.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.polyglotmesh.config.ExecutionProperties;
import com.polyglotmesh.config.GraalVmProperties;
import com.polyglotmesh.model.ExecutionResult;
import com.polyglotmesh.model.ExecutionStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Real Java compilation and execution through the JDK toolchain: output,
 * standard input, package handling, diagnostics, process isolation, compile and
 * execution timeouts, output capping and temp-directory cleanup.
 */
class JavaExecutionSandboxTest {

    private static JavaExecutionSandbox sandbox;

    @BeforeAll
    static void setUp() {
        sandbox = new JavaExecutionSandbox(
                properties(Duration.ofSeconds(15), 10_000), executionProperties());
    }

    @AfterAll
    static void tearDown() {
        sandbox.close();
    }

    @Test
    void runsHelloWorld() {
        ExecutionResult result = sandbox.execute("""
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("Hello PolyglotMesh");
                    }
                }
                """, "");

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("Hello PolyglotMesh\n");
        assertThat(result.stderr()).isEmpty();
        assertThat(result.error()).isNull();
        assertThat(result.status()).isEqualTo(ExecutionStatus.SUCCESS);
    }

    @Test
    void separatesStandardOutputAndStandardError() {
        ExecutionResult result = sandbox.execute("""
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("to stdout");
                        System.err.println("to stderr");
                    }
                }
                """, "");

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("to stdout\n");
        assertThat(result.stderr()).isEqualTo("to stderr\n");
        assertThat(result.error()).isNull();
        assertThat(result.status()).isEqualTo(ExecutionStatus.SUCCESS);
    }

    @Test
    void readsStandardInputWithScanner() {
        ExecutionResult result = sandbox.execute("""
                import java.util.Scanner;

                public class Main {
                    public static void main(String[] args) {
                        Scanner scanner = new Scanner(System.in);
                        String name = scanner.nextLine();
                        System.out.println(name.toUpperCase());
                    }
                }
                """, "ada\n");

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("ADA\n");
    }

    @Test
    void runsPublicClassWithItsOwnName() {
        ExecutionResult result = sandbox.execute("""
                public class Greeter {
                    public static void main(String[] args) {
                        System.out.println("custom name");
                    }
                }
                """, "");

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("custom name\n");
    }

    @Test
    void runsPackagedClass() {
        ExecutionResult result = sandbox.execute("""
                package demo.app;

                public class App {
                    public static void main(String[] args) {
                        System.out.println("packaged");
                    }
                }
                """, "");

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("packaged\n");
    }

    @Test
    void reportsCompilationErrorsWithLineNumbers() {
        ExecutionResult result = sandbox.execute("""
                public class Main {
                    public static void main(String[] args) {
                        int value = ;
                    }
                }
                """, "");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("line 3");
        assertThat(result.status()).isEqualTo(ExecutionStatus.COMPILE_ERROR);
    }

    @Test
    void reportsRuntimeExceptionsAndKeepsPartialOutput() {
        ExecutionResult result = sandbox.execute("""
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("before");
                        int value = 1 / 0;
                    }
                }
                """, "");

        assertThat(result.success()).isFalse();
        assertThat(result.output()).isEqualTo("before\n");
        assertThat(result.error()).contains("ArithmeticException");
        assertThat(result.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
        assertThat(result.error()).doesNotContain("com.polyglotmesh");
    }

    @Test
    void reportsNonZeroExitStatus() {
        ExecutionResult result = sandbox.execute("""
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("bye");
                        System.exit(3);
                    }
                }
                """, "");

        assertThat(result.success()).isFalse();
        assertThat(result.output()).isEqualTo("bye\n");
        assertThat(result.error()).contains("status 3");
        assertThat(result.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
    }

    @Test
    void isolatesSystemExitFromTheBackend() {
        ExecutionResult exited = sandbox.execute("""
                public class Main {
                    public static void main(String[] args) {
                        System.exit(0);
                    }
                }
                """, "");
        ExecutionResult after = sandbox.execute("""
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("still alive");
                    }
                }
                """, "");

        assertThat(exited.success()).isTrue();
        assertThat(exited.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(after.success()).isTrue();
        assertThat(after.output()).isEqualTo("still alive\n");
        assertThat(after.status()).isEqualTo(ExecutionStatus.SUCCESS);
    }

    @Test
    void rejectsSourceWithoutATypeDeclaration() {
        ExecutionResult result = sandbox.execute("System.out.println(\"hi\");", "");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("No top-level");
        assertThat(result.status()).isEqualTo(ExecutionStatus.COMPILE_ERROR);
    }

    @Test
    void truncatesLargeOutput() {
        JavaExecutionSandbox small = new JavaExecutionSandbox(
                properties(Duration.ofSeconds(15), 50), executionProperties());
        try {
            ExecutionResult result = small.execute("""
                    public class Main {
                        public static void main(String[] args) {
                            System.out.print("x".repeat(500));
                        }
                    }
                    """, "");

            assertThat(result.success()).isTrue();
            assertThat(result.output()).endsWith("[output truncated]");
            assertThat(result.output().length()).isLessThan(80);
            assertThat(result.status()).isEqualTo(ExecutionStatus.SUCCESS);
        } finally {
            small.close();
        }
    }

    @Test
    @Timeout(60)
    void cancelsInfiniteLoops() {
        JavaExecutionSandbox quick = new JavaExecutionSandbox(
                properties(Duration.ofSeconds(15), 1_000),
                executionProperties(Duration.ofSeconds(30), Duration.ofSeconds(1)));
        try {
            long startedAt = System.nanoTime();
            ExecutionResult result = quick.execute("""
                    public class Main {
                        public static void main(String[] args) {
                            while (true) {
                                // spin
                            }
                        }
                    }
                    """, "");
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

            assertThat(result.success()).isFalse();
            // The run budget, not the compile budget, must have stopped it.
            assertThat(result.error()).contains("Execution timed out");
            assertThat(result.status()).isEqualTo(ExecutionStatus.TIMEOUT);
            // The wall clock also covers compilation and child-JVM startup, so
            // this bound only proves the loop was stopped rather than left
            // running; the exact number varies with host load.
            assertThat(elapsedMs)
                    .as("infinite loop must be cancelled, not left running")
                    .isLessThan(25_000);
        } finally {
            quick.close();
        }
    }

    @Test
    void cancelsCompilationThatExceedsItsBudget() {
        JavaExecutionSandbox impatient = new JavaExecutionSandbox(
                properties(Duration.ofSeconds(15), 10_000),
                executionProperties(Duration.ofNanos(1), Duration.ofSeconds(10)));
        try {
            ExecutionResult result = impatient.execute("""
                    public class Main {
                        public static void main(String[] args) {
                            System.out.println("too late");
                        }
                    }
                    """, "");

            assertThat(result.success()).isFalse();
            assertThat(result.error()).contains("Compilation timed out");
            assertThat(result.status()).isEqualTo(ExecutionStatus.TIMEOUT);
        } finally {
            impatient.close();
        }
    }

    @Test
    void sweepsStaleWorkDirectoriesOnStartup() throws IOException {
        Path stale = Files.createTempDirectory("polyglotmesh-java-stale-");
        Files.setLastModifiedTime(
                stale, FileTime.fromMillis(System.currentTimeMillis() - Duration.ofHours(2).toMillis()));

        JavaExecutionSandbox sweeper = new JavaExecutionSandbox(
                properties(Duration.ofSeconds(15), 10_000), executionProperties());
        try {
            assertThat(Files.exists(stale)).isFalse();
        } finally {
            sweeper.close();
            Files.deleteIfExists(stale);
        }
    }

    private static GraalVmProperties properties(Duration timeout, int maxOutputChars) {
        return new GraalVmProperties(
                true, Duration.ofSeconds(60), timeout, maxOutputChars, 2_000);
    }

    private static ExecutionProperties executionProperties() {
        return executionProperties(Duration.ofSeconds(30));
    }

    private static ExecutionProperties executionProperties(Duration compileTimeout) {
        return executionProperties(compileTimeout, Duration.ofSeconds(10));
    }

    private static ExecutionProperties executionProperties(
            Duration compileTimeout, Duration javaExecutionTimeout) {
        return new ExecutionProperties(
                true,
                60,
                Duration.ofMinutes(1),
                4,
                Duration.ofSeconds(2),
                compileTimeout,
                javaExecutionTimeout,
                Duration.ofHours(1));
    }
}
