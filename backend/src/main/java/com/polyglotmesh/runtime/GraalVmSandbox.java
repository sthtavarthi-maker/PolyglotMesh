package com.polyglotmesh.runtime;

import com.polyglotmesh.config.GraalVmProperties;
import com.polyglotmesh.model.ExecutionResult;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs untrusted source in a locked-down GraalVM context.
 *
 * <p>Every execution gets a fresh {@link Context}, so globals cannot leak from
 * one request to the next. The context denies host access, host class lookup,
 * file and network IO, process creation, native access, thread creation and
 * environment variables. GraalPy's Python-visible environment is additionally
 * cleared, because its posix backend reads the host environment independently of
 * {@link EnvironmentAccess#NONE}. Output is captured in memory, never inherited
 * from the server's console.</p>
 *
 * <p>A wall-clock timeout cancels the evaluation via
 * {@link Context#close(boolean)}; the worker thread is a daemon so a stuck guest
 * cannot keep the JVM alive. This is defence in depth, not a hardened container:
 * the code still runs inside the backend JVM, so CPU and memory limits are
 * documented as later work rather than claimed.</p>
 */
@Component
public class GraalVmSandbox implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(GraalVmSandbox.class);

    private static final String OUTPUT_TRUNCATED = "\n[output truncated]";
    private static final String ERROR_TRUNCATED = "\n[error truncated]";

    private final GraalVmEngine engine;
    private final GraalVmProperties properties;
    private final ExecutorService workers;

    public GraalVmSandbox(GraalVmEngine engine, GraalVmProperties properties) {
        this.engine = engine;
        this.properties = properties;
        this.workers = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "polyglotmesh-sandbox");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Evaluates {@code source} and reports the captured console output.
     *
     * <p>{@code polyglotLanguageId} must be a language the engine reports as
     * available; callers check that before calling.</p>
     */
    public ExecutionResult execute(String polyglotLanguageId, String source, String stdin) {
        long startedAt = System.nanoTime();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        Context context = null;
        Future<?> evaluation = null;
        try {
            context = newContext(polyglotLanguageId, out, err);
            installStandardInput(context, polyglotLanguageId, stdin == null ? "" : stdin);

            Context evaluating = context;
            evaluation = workers.submit(() -> evaluating.eval(polyglotLanguageId, source));
            evaluation.get(properties.timeoutMillis(), TimeUnit.MILLISECONDS);

            return ExecutionResult.success(captured(out, err), elapsedMillis(startedAt));
        } catch (TimeoutException timeout) {
            cancel(evaluation, context);
            return ExecutionResult.failure(
                    captured(out, err),
                    "Execution timed out after " + properties.timeoutMillis() + " ms.",
                    elapsedMillis(startedAt));
        } catch (ExecutionException failure) {
            return ExecutionResult.failure(
                    captured(out, err),
                    describe(failure.getCause()),
                    elapsedMillis(startedAt));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            cancel(evaluation, context);
            return ExecutionResult.failure(
                    captured(out, err),
                    "Execution was interrupted.",
                    elapsedMillis(startedAt));
        } catch (PolyglotException exception) {
            // Raised on this thread while installing stdin, not by the guest.
            return ExecutionResult.failure(
                    captured(out, err),
                    describe(exception),
                    elapsedMillis(startedAt));
        } catch (RuntimeException exception) {
            log.error("Sandbox setup failed for language '{}'", polyglotLanguageId, exception);
            return ExecutionResult.failure(
                    captured(out, err),
                    "Execution failed on the server.",
                    elapsedMillis(startedAt));
        } finally {
            closeQuietly(context);
        }
    }

    private Context newContext(
            String polyglotLanguageId, ByteArrayOutputStream out, ByteArrayOutputStream err) {
        return Context.newBuilder(polyglotLanguageId)
                .engine(engine.engine())
                // Explicit even though the default is false: this assertion makes
                // it impossible to widen host access by accident later.
                .allowAllAccess(false)
                .allowHostAccess(HostAccess.NONE)
                .allowHostClassLookup(className -> false)
                .allowHostClassLoading(false)
                .allowIO(IOAccess.NONE)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .allowNativeAccess(false)
                .allowEnvironmentAccess(EnvironmentAccess.NONE)
                .allowExperimentalOptions(false)
                .out(out)
                .err(err)
                .build();
    }

    /**
     * Makes standard input available to the guest.
     *
     * <p>Each embedded language gets an idiom-appropriate bridge: JavaScript
     * uses {@code readLine()}/{@code readAll()}, Python receives a real
     * {@code sys.stdin} so the built-in {@code input()} works. Other languages
     * get their bridge together with their adapters.</p>
     */
    private void installStandardInput(Context context, String polyglotLanguageId, String stdin) {
        switch (polyglotLanguageId) {
            case "js" -> installJavaScriptStdin(context, stdin);
            case "python" -> installPythonStdin(context, stdin);
            default -> {
                // No bridge yet for this language.
            }
        }
    }

    private void installJavaScriptStdin(Context context, String stdin) {
        Value bindings = context.getBindings("js");
        bindings.putMember("__polyglotmeshStdin", stdin);
        context.eval("js", """
                (() => {
                  const stdin = String(globalThis.__polyglotmeshStdin ?? '');
                  const lines = stdin.length === 0 ? [] : stdin.split(/\\r?\\n/);
                  // A trailing newline terminates the last line; it does not
                  // create an extra empty line.
                  if (lines.length > 0 && lines[lines.length - 1] === '') {
                    lines.pop();
                  }
                  let index = 0;
                  globalThis.readLine = () => (index < lines.length ? lines[index++] : null);
                  globalThis.readAll = () => lines.slice(index).join('\\n');
                  try { delete globalThis.__polyglotmeshStdin; } catch (ignored) {}
                })();
                """);
    }

    private void installPythonStdin(Context context, String stdin) {
        context.getBindings("python").putMember("__polyglotmesh_stdin", stdin);
        context.eval("python", """
                import io as __polyglotmesh_io
                import sys as __polyglotmesh_sys

                # GraalPy's posix backend reads the host environment directly and
                # is not covered by Context.allowEnvironmentAccess(NONE). Clear the
                # Python-visible copies before any guest code runs so os.getenv()
                # and os.environ cannot read backend secrets.
                import os as __polyglotmesh_os
                try:
                    __polyglotmesh_os.environ.clear()
                except Exception:
                    pass
                try:
                    __polyglotmesh_os.environb.clear()
                except Exception:
                    pass
                __polyglotmesh_mod = None
                for __polyglotmesh_name in ('posix', 'nt'):
                    try:
                        __polyglotmesh_mod = __import__(__polyglotmesh_name)
                        __polyglotmesh_mod.environ.clear()
                    except Exception:
                        pass
                del __polyglotmesh_name, __polyglotmesh_mod, __polyglotmesh_os

                __polyglotmesh_sys.stdin = __polyglotmesh_io.StringIO(__polyglotmesh_stdin)
                del __polyglotmesh_io, __polyglotmesh_sys, __polyglotmesh_stdin
                """);
    }

    private void cancel(Future<?> evaluation, Context context) {
        // Closing with cancel=true interrupts the running guest, which is the
        // only reliable way to stop an infinite loop.
        closeQuietly(context);
        if (evaluation != null) {
            evaluation.cancel(true);
        }
    }

    private void closeQuietly(Context context) {
        if (context == null) {
            return;
        }
        try {
            context.close(true);
        } catch (RuntimeException exception) {
            log.debug("Context was already closed: {}", exception.getMessage());
        }
    }

    private String captured(ByteArrayOutputStream out, ByteArrayOutputStream err) {
        String stdout = out.toString(StandardCharsets.UTF_8);
        String stderr = err.toString(StandardCharsets.UTF_8);
        String combined = stderr.isEmpty() ? stdout : stdout + stderr;
        if (combined.length() <= properties.maxOutputChars()) {
            return combined;
        }
        return combined.substring(0, properties.maxOutputChars()) + OUTPUT_TRUNCATED;
    }

    private String describe(Throwable cause) {
        if (cause instanceof PolyglotException exception) {
            String message = exception.getMessage();
            if (message == null || message.isBlank()) {
                message = exception.isSyntaxError() ? "Syntax error." : "Execution failed.";
            }
            return limit(message.strip(), properties.maxErrorChars(), ERROR_TRUNCATED);
        }
        log.error("Unexpected error inside the sandbox", cause);
        return "Execution failed on the server.";
    }

    private String limit(String text, int max, String suffix) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + suffix;
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    @PreDestroy
    @Override
    public void close() {
        workers.shutdownNow();
    }
}
