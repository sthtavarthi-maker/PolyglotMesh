package com.polyglotmesh.runtime;

import com.polyglotmesh.config.GraalVmProperties;
import com.polyglotmesh.model.Language;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.graalvm.polyglot.Engine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Owns the single GraalVM polyglot engine used by the application.
 *
 * <p>An {@link Engine} is expensive to create and thread-safe, so it is built
 * lazily once and shared. Each execution still gets its own {@code Context},
 * which is where actual isolation lives. Creating the engine lazily keeps the
 * application (and tests that never execute code) from paying for it.</p>
 *
 * <p>Availability checks look for the bundled language provider classes instead
 * of creating the engine, so {@code GET /api/languages} and the 501 decision do
 * not trigger a multi-second engine start. This build bundles GraalJS and
 * GraalPy, so {@code js} and {@code python} answer true. TruffleRuby is not
 * bundled because its 23.1 runtime does not support Windows.</p>
 */
@Component
public class GraalVmEngine {

    private static final Logger log = LoggerFactory.getLogger(GraalVmEngine.class);

    private static final Map<String, String> LANGUAGE_PROVIDERS = Map.of(
            "js", "com.oracle.truffle.js.lang.JavaScriptLanguage",
            "python", "com.oracle.graal.python.PythonLanguage");

    private final GraalVmProperties properties;
    private final Object lock = new Object();

    private volatile Engine engine;
    private volatile boolean initializationFailed;

    public GraalVmEngine(GraalVmProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    /**
     * Shared engine instance. Callers must not close it; lifecycle is managed
     * here.
     *
     * @throws IllegalStateException when polyglot execution is disabled
     */
    public Engine engine() {
        if (!isEnabled()) {
            throw new IllegalStateException("GraalVM execution is disabled.");
        }
        Engine current = engine;
        if (current != null) {
            return current;
        }
        synchronized (lock) {
            if (engine == null) {
                engine = Engine.newBuilder()
                        // The application normally runs on a stock JDK here; the
                        // fallback runtime is expected and documented.
                        .option("engine.WarnInterpreterOnly", "false")
                        .build();
            }
            return engine;
        }
    }

    /** True when the engine could be created and at least one language exists. */
    public boolean isAvailable() {
        return !availableLanguageIds().isEmpty();
    }

    /** Language ids installed on the classpath, e.g. {@code js}. */
    public Set<String> availableLanguageIds() {
        if (!isEnabled() || initializationFailed) {
            return Set.of();
        }
        try {
            return Set.copyOf(engine().getLanguages().keySet());
        } catch (RuntimeException exception) {
            initializationFailed = true;
            log.warn("GraalVM polyglot engine could not be created: {}", exception.getMessage());
            return Set.of();
        }
    }

    /** Whether the bundled runtime can serve the given polyglot language (never throws). */
    public boolean supports(String polyglotLanguageId) {
        return isEnabled() && !initializationFailed && providerPresent(polyglotLanguageId);
    }

    /**
     * Checks for the language's provider class without initialising it. This is
     * a cheap, side-effect-free availability signal; actual execution remains
     * the final authority.
     */
    static boolean providerPresent(String polyglotLanguageId) {
        if (polyglotLanguageId == null) {
            return false;
        }
        String className = LANGUAGE_PROVIDERS.get(polyglotLanguageId);
        if (className == null) {
            return false;
        }
        try {
            Class.forName(className, false, GraalVmEngine.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError unavailable) {
            return false;
        }
    }

    /**
     * Polyglot id for languages the embedded engine can serve.
     *
     * <p>Java is deliberately absent: Java programs are compiled with the JDK
     * compiler and run in a separate child JVM, not as a Truffle language. C,
     * C++, Rust, Go and R need external toolchains, so they have no polyglot id
     * either.</p>
     */
    public static Optional<String> polyglotLanguageId(Language language) {
        return switch (language) {
            case JAVASCRIPT -> Optional.of("js");
            case PYTHON -> Optional.of("python");
            case RUBY -> Optional.of("ruby");
            default -> Optional.empty();
        };
    }

    @PreDestroy
    public void close() {
        Engine current;
        synchronized (lock) {
            current = engine;
            engine = null;
        }
        if (current != null) {
            // Cancel anything still running before tearing the engine down.
            current.close(true);
        }
    }
}
