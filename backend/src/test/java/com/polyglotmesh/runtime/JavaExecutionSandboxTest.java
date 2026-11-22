package com.polyglotmesh.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.polyglotmesh.config.GraalVmProperties;
import com.polyglotmesh.model.ExecutionResult;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Real Java compilation and execution through the JDK toolchain: output,
 * standard input, package handling, diagnostics, process isolation, timeout
 * cancellation and output capping.
 */
class JavaExecutionSandboxTest {

    private static JavaExecutionSandbox sandbox;

    @BeforeAll
    static void setUp() {
        sandbox = new JavaExecutionSandbox(properties(Duration.ofSeconds(15), 10_000));
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
        assertThat(result.error()).isNull();
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
        assertThat(after.success()).isTrue();
        assertThat(after.output()).isEqualTo("still alive\n");
    }

    @Test
    void rejectsSourceWithoutATypeDeclaration() {
        ExecutionResult result = sandbox.execute("System.out.println(\"hi\");", "");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("No top-level");
    }

    @Test
    void truncatesLargeOutput() {
        JavaExecutionSandbox small =
                new JavaExecutionSandbox(properties(Duration.ofSeconds(15), 50));
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
    }

    @Test
    @Timeout(30)
    void cancelsInfiniteLoops() {
        JavaExecutionSandbox quick =
                new JavaExecutionSandbox(properties(Duration.ofSeconds(1), 1_000));
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
        assertThat(result.error()).contains("timed out");
        assertThat(elapsedMs).isLessThan(10_000);
    }

    private static GraalVmProperties properties(Duration timeout, int maxOutputChars) {
        return new GraalVmProperties(true, timeout, maxOutputChars, 2_000);
    }
}
