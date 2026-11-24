package com.polyglotmesh.runtime;

import com.polyglotmesh.config.ExecutionProperties;
import com.polyglotmesh.config.GraalVmProperties;
import com.polyglotmesh.model.ExecutionResult;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Compiles and runs Java programs with the JDK toolchain.
 *
 * <p>Compilation happens in-process through {@link JavaCompiler}; execution
 * happens in a <strong>child JVM</strong>. That separation matters: guest code
 * calling {@link System#exit(int)} or crashing the JVM cannot take the backend
 * down, and the parent can kill the child on timeout. The child gets a heap cap,
 * a private working directory, a temporary directory of its own and a cleared
 * environment.</p>
 *
 * <p>This is process isolation, not a security sandbox: the child can still
 * read and write files and open network connections as the backend user. That
 * limitation is documented in the README and is not hidden behind a "sandbox"
 * claim.</p>
 *
 * <p>Compilation is bounded by {@code polyglotmesh.execution.compile-timeout}
 * and runs on a small daemon pool. A pathological compiler task cannot hold the
 * request thread, and the bounded pool prevents runaway compile threads.
 * Temporary directories and child processes are tracked so shutdown can clean
 * them up; stale directories from a previous crash are swept at startup.</p>
 */
@Component
public class JavaExecutionSandbox {

    private static final Logger log = LoggerFactory.getLogger(JavaExecutionSandbox.class);

    /** Entry point is the public top-level type, or the first type if none is public. */
    private static final Pattern PUBLIC_TYPE = Pattern.compile(
            "\\bpublic\\s+(?:(?:final|abstract|sealed|non-sealed|static)\\s+)*"
                    + "(?:class|record|enum|interface)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
    private static final Pattern ANY_TYPE = Pattern.compile(
            "\\b(?:(?:final|abstract|sealed|non-sealed|static)\\s+)*"
                    + "(?:class|record|enum|interface)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
    private static final Pattern PACKAGE = Pattern.compile(
            "^\\s*package\\s+([A-Za-z_$][A-Za-z0-9_.$]*)\\s*;", Pattern.MULTILINE);

    private static final String WORK_DIR_PREFIX = "polyglotmesh-java-";
    private static final int CHILD_HEAP_MB = 128;
    private static final long DEFERRED_CLEANUP_SECONDS = 30;
    private static final String OUTPUT_TRUNCATED = "\n[output truncated]";
    private static final String ERROR_TRUNCATED = "\n[error truncated]";

    private final GraalVmProperties properties;
    private final ExecutionProperties executionProperties;
    private final boolean available;
    private final Path javaLauncher;
    private final ExecutorService compilerExecutor;
    private final ScheduledExecutorService cleanupExecutor;
    private final Set<Process> runningProcesses = ConcurrentHashMap.newKeySet();
    private final Set<Path> activeWorkDirs = ConcurrentHashMap.newKeySet();
    private final Set<Path> pendingCleanup = ConcurrentHashMap.newKeySet();

    public JavaExecutionSandbox(
            GraalVmProperties properties, ExecutionProperties executionProperties) {
        this.properties = properties;
        this.executionProperties = executionProperties;
        this.available = ToolProvider.getSystemJavaCompiler() != null;
        this.javaLauncher = locateJavaLauncher();
        this.compilerExecutor = Executors.newFixedThreadPool(
                Math.max(1, executionProperties.maxConcurrentExecutions()),
                daemonThreads("polyglotmesh-javac"));
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(
                daemonThreads("polyglotmesh-cleanup"));
        sweepStaleWorkDirs();
    }

    /** True when this runtime has both a Java compiler and a Java launcher. */
    public boolean isAvailable() {
        return available && javaLauncher != null;
    }

    /**
     * Compiles and runs one program in an isolated child JVM.
     *
     * @param source program text; the entry class must declare
     *               {@code public static void main(String[] args)}
     * @param stdin  text piped to the child's standard input
     */
    public ExecutionResult execute(String source, String stdin) {
        long startedAt = System.nanoTime();
        String typeName = entryClassName(source);
        if (typeName == null) {
            return ExecutionResult.failure(
                    "No top-level type declaration found.", elapsedMillis(startedAt));
        }

        Path workDir = null;
        boolean deferCleanup = false;
        try {
            workDir = Files.createTempDirectory(WORK_DIR_PREFIX);
            activeWorkDirs.add(workDir);
            Path classesDir = Files.createDirectories(workDir.resolve("classes"));
            Path sourceFile = workDir.resolve(typeName + ".java");
            Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

            CompileOutcome compilation = compile(sourceFile, classesDir);
            if (compilation.error() != null) {
                // A timed-out compiler may still be touching its source and
                // class files, so let it finish before removing the directory.
                deferCleanup = compilation.timedOut();
                return ExecutionResult.failure(
                        compilation.error(), elapsedMillis(startedAt));
            }
            return run(launchName(typeName, source), classesDir, workDir, stdin, startedAt);
        } catch (IOException exception) {
            log.error("Java execution setup failed", exception);
            return ExecutionResult.failure(
                    "Execution failed on the server.", elapsedMillis(startedAt));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return ExecutionResult.failure(
                    "Execution was interrupted.", elapsedMillis(startedAt));
        } finally {
            if (workDir != null) {
                activeWorkDirs.remove(workDir);
                if (deferCleanup) {
                    scheduleCleanup(workDir);
                } else {
                    deleteRecursively(workDir);
                }
            }
        }
    }

    /** First public type in the source, else the first type declaration. */
    static String entryClassName(String source) {
        Matcher publicType = PUBLIC_TYPE.matcher(source);
        if (publicType.find()) {
            return publicType.group(1);
        }
        Matcher anyType = ANY_TYPE.matcher(source);
        return anyType.find() ? anyType.group(1) : null;
    }

    /** Fully qualified entry point, honouring an optional package declaration. */
    static String launchName(String simpleName, String source) {
        Matcher packageName = PACKAGE.matcher(source);
        return packageName.find() ? packageName.group(1) + "." + simpleName : simpleName;
    }

    private CompileOutcome compile(Path sourceFile, Path classesDir) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return new CompileOutcome("No Java compiler is available on this server.", false);
        }

        Future<String> task = compilerExecutor.submit(
                () -> compileNow(compiler, sourceFile, classesDir));
        try {
            String error = task.get(executionProperties.compileTimeoutNanos(), TimeUnit.NANOSECONDS);
            return new CompileOutcome(error, false);
        } catch (TimeoutException timeout) {
            task.cancel(true);
            long timeoutMs = Math.max(
                    1, TimeUnit.NANOSECONDS.toMillis(executionProperties.compileTimeoutNanos()));
            return new CompileOutcome(
                    "Compilation timed out after " + timeoutMs + " ms.", true);
        } catch (ExecutionException failure) {
            log.error("Java compilation failed", failure.getCause());
            return new CompileOutcome("Execution failed on the server.", false);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            task.cancel(true);
            return new CompileOutcome("Execution was interrupted.", false);
        }
    }

    private String compileNow(JavaCompiler compiler, Path sourceFile, Path classesDir)
            throws IOException {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units =
                    fileManager.getJavaFileObjects(sourceFile.toFile());
            List<String> options = List.of(
                    "-d", classesDir.toString(),
                    "-encoding", "UTF-8",
                    "--release", String.valueOf(Runtime.version().feature()),
                    "-proc:none");
            boolean compiled = compiler.getTask(
                    null, fileManager, diagnostics, options, null, units).call();
            if (compiled) {
                return null;
            }
            return formatDiagnostics(diagnostics);
        }
    }

    private String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder message = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() != Diagnostic.Kind.ERROR) {
                continue;
            }
            if (!message.isEmpty()) {
                message.append('\n');
            }
            if (diagnostic.getLineNumber() > 0) {
                message.append("line ").append(diagnostic.getLineNumber()).append(": ");
            }
            message.append(diagnostic.getMessage(Locale.ROOT));
        }
        if (message.isEmpty()) {
            message.append("Compilation failed.");
        }
        return limit(message.toString(), properties.maxErrorChars(), ERROR_TRUNCATED);
    }

    private ExecutionResult run(
            String className, Path classesDir, Path workDir, String stdin, long startedAt)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(javaLauncher.toString());
        command.add("-Xmx" + CHILD_HEAP_MB + "m");
        command.add("-XX:+UseSerialGC");
        command.add("-Dfile.encoding=UTF-8");
        command.add("-Djava.io.tmpdir=" + workDir);
        command.add("-cp");
        command.add(classesDir.toString());
        command.add(className);

        ProcessBuilder builder = new ProcessBuilder(command).directory(workDir.toFile());
        // Do not leak the backend's environment to guest code.
        builder.environment().clear();
        builder.environment().put("TEMP", workDir.toString());
        builder.environment().put("TMP", workDir.toString());

        Process process = builder.start();
        runningProcesses.add(process);
        try {
            StreamCollector stdout = new StreamCollector(
                    process.getInputStream(), properties.maxOutputChars(), OUTPUT_TRUNCATED);
            StreamCollector stderr = new StreamCollector(
                    process.getErrorStream(), properties.maxErrorChars(), ERROR_TRUNCATED);
            Thread stdoutReader = stdout.start("polyglotmesh-java-stdout");
            Thread stderrReader = stderr.start("polyglotmesh-java-stderr");

            try (OutputStream childStdin = process.getOutputStream()) {
                childStdin.write(
                        (stdin == null ? "" : stdin).getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // The child may exit before reading input; its exit status reports that.
            }

            boolean finished =
                    process.waitFor(properties.timeoutMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
            stdoutReader.join(2_000);
            stderrReader.join(2_000);

            long elapsed = elapsedMillis(startedAt);
            String output = normalizeNewlines(stdout.text());
            String error = normalizeNewlines(stderr.text());

            if (!finished) {
                return ExecutionResult.failure(
                        output,
                        "Execution timed out after " + properties.timeoutMillis() + " ms.",
                        elapsed);
            }
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                return ExecutionResult.success(output + error, elapsed);
            }
            String failure = error.isBlank()
                    ? "Program exited with status " + exitCode + "."
                    : error;
            return ExecutionResult.failure(output, failure, elapsed);
        } finally {
            runningProcesses.remove(process);
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static Path locateJavaLauncher() {
        String executable = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win") ? "java.exe" : "java";
        Path launcher = Path.of(System.getProperty("java.home"), "bin", executable);
        return Files.isRegularFile(launcher) ? launcher : null;
    }

    private void scheduleCleanup(Path workDir) {
        pendingCleanup.add(workDir);
        try {
            cleanupExecutor.schedule(() -> {
                pendingCleanup.remove(workDir);
                deleteRecursively(workDir);
            }, DEFERRED_CLEANUP_SECONDS, TimeUnit.SECONDS);
        } catch (RejectedExecutionException shuttingDown) {
            pendingCleanup.remove(workDir);
            deleteRecursively(workDir);
        }
    }

    /**
     * Removes work directories abandoned by a previous run (for example after a
     * crash or a forced shutdown). Recent directories are left alone so a
     * concurrently starting instance is not disturbed.
     */
    private void sweepStaleWorkDirs() {
        String tmp = System.getProperty("java.io.tmpdir");
        if (tmp == null) {
            return;
        }
        Path root = Path.of(tmp);
        long cutoff = System.currentTimeMillis() - executionProperties.staleWorkDirAgeMillis();
        try (Stream<Path> entries = Files.list(root)) {
            entries.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith(WORK_DIR_PREFIX))
                    .filter(path -> lastModified(path) < cutoff)
                    .forEach(JavaExecutionSandbox::deleteRecursively);
        } catch (IOException exception) {
            log.debug("Could not sweep stale Java work directories: {}", exception.getMessage());
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static String limit(String text, int max, String suffix) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + suffix;
    }

    /** Child JVMs use the host line separator; the API keeps the Unix convention. */
    private static String normalizeNewlines(String text) {
        return text.replace("\r\n", "\n");
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private static ThreadFactory daemonThreads(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static void deleteRecursively(Path root) {
        if (root == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best effort: a compiler may still be finishing, or the OS holds a lock.
                }
            });
        } catch (IOException exception) {
            log.debug("Could not remove temporary directory {}: {}", root, exception.getMessage());
        }
    }

    /** Stops compiler/cleanup threads and removes anything still tracked. */
    @PreDestroy
    public void close() {
        compilerExecutor.shutdownNow();
        cleanupExecutor.shutdownNow();
        for (Process process : runningProcesses) {
            process.destroyForcibly();
        }
        runningProcesses.clear();
        for (Path workDir : activeWorkDirs) {
            deleteRecursively(workDir);
        }
        activeWorkDirs.clear();
        for (Path workDir : pendingCleanup) {
            deleteRecursively(workDir);
        }
        pendingCleanup.clear();
    }

    private record CompileOutcome(String error, boolean timedOut) {
    }

    /** Drains a child stream, keeping at most {@code maxChars} characters. */
    private static final class StreamCollector implements Runnable {

        private final InputStream stream;
        private final int maxChars;
        private final String suffix;
        private final StringBuilder kept = new StringBuilder();

        private volatile boolean truncated;
        private Thread thread;

        StreamCollector(InputStream stream, int maxChars, String suffix) {
            this.stream = stream;
            this.maxChars = maxChars;
            this.suffix = suffix;
        }

        Thread start(String name) {
            thread = new Thread(this, name);
            thread.setDaemon(true);
            thread.start();
            return thread;
        }

        @Override
        public void run() {
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                char[] buffer = new char[4_096];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    synchronized (kept) {
                        int room = maxChars - kept.length();
                        if (room > 0) {
                            kept.append(buffer, 0, Math.min(room, read));
                        }
                        if (read > room) {
                            truncated = true;
                        }
                    }
                }
            } catch (IOException exception) {
                // Stream closed because the child was killed; the exit status covers it.
            }
        }

        String text() {
            if (thread != null) {
                try {
                    thread.join(2_000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            synchronized (kept) {
                return kept + (truncated ? suffix : "");
            }
        }
    }
}
