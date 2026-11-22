package com.polyglotmesh.runtime;

import com.polyglotmesh.config.GraalVmProperties;
import com.polyglotmesh.model.ExecutionResult;
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
import java.util.concurrent.TimeUnit;
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
            "^\\s*package\\s+([A-Za-z_$][A-Za-z0-9_$.]*)\\s*;", Pattern.MULTILINE);

    private static final int CHILD_HEAP_MB = 128;
    private static final String OUTPUT_TRUNCATED = "\n[output truncated]";
    private static final String ERROR_TRUNCATED = "\n[error truncated]";

    private final GraalVmProperties properties;
    private final boolean available;
    private final Path javaLauncher;

    public JavaExecutionSandbox(GraalVmProperties properties) {
        this.properties = properties;
        this.available = ToolProvider.getSystemJavaCompiler() != null;
        this.javaLauncher = locateJavaLauncher();
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
        String className = entryClassName(source);
        if (className == null) {
            return ExecutionResult.failure(
                    "No top-level type declaration found.", elapsedMillis(startedAt));
        }

        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("polyglotmesh-java-");
            Path classesDir = Files.createDirectories(workDir.resolve("classes"));
            Path sourceFile = workDir.resolve(className + ".java");
            Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

            String compilationError = compile(sourceFile, classesDir);
            if (compilationError != null) {
                return ExecutionResult.failure(compilationError, elapsedMillis(startedAt));
            }
            return run(launchName(className, source), classesDir, workDir, stdin, startedAt);
        } catch (IOException exception) {
            log.error("Java execution setup failed", exception);
            return ExecutionResult.failure(
                    "Execution failed on the server.", elapsedMillis(startedAt));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return ExecutionResult.failure(
                    "Execution was interrupted.", elapsedMillis(startedAt));
        } finally {
            deleteRecursively(workDir);
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

    private String compile(Path sourceFile, Path classesDir) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return "No Java compiler is available on this server.";
        }
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
        StreamCollector stdout =
                new StreamCollector(process.getInputStream(), properties.maxOutputChars(), OUTPUT_TRUNCATED);
        StreamCollector stderr =
                new StreamCollector(process.getErrorStream(), properties.maxErrorChars(), ERROR_TRUNCATED);
        Thread stdoutReader = stdout.start("polyglotmesh-java-stdout");
        Thread stderrReader = stderr.start("polyglotmesh-java-stderr");

        try (OutputStream childStdin = process.getOutputStream()) {
            childStdin.write((stdin == null ? "" : stdin).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // The child may exit before reading input; that is reported by its exit status.
        }

        boolean finished = process.waitFor(properties.timeoutMillis(), TimeUnit.MILLISECONDS);
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
    }

    private static Path locateJavaLauncher() {
        String executable = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win") ? "java.exe" : "java";
        Path launcher = Path.of(System.getProperty("java.home"), "bin", executable);
        return Files.isRegularFile(launcher) ? launcher : null;
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

    private static void deleteRecursively(Path root) {
        if (root == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best effort: temp files are also cleaned by the OS.
                }
            });
        } catch (IOException exception) {
            log.debug("Could not remove temporary directory {}: {}", root, exception.getMessage());
        }
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
