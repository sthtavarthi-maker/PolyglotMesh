package com.polyglotmesh.runtime;

import com.polyglotmesh.config.GraalVmProperties;
import com.polyglotmesh.model.Language;
import jakarta.annotation.PreDestroy;
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
 * <p>Language availability is reported from the engine itself, not from a hard
 * coded list: a language is available only when its artifacts are on the
 * classpath. Day 12 bundles GraalJS and GraalPy, so {@code js} and
 * {@code python} answer true. TruffleRuby is not bundled because its 23.1
 * runtime does not support Windows.</p>
 */
@Component
public class GraalVmEngine {

    private static final Logger log = LoggerFactory.getLogger(GraalVmEngine.class);

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

    /** Whether the engine has the given polyglot language (never throws). */
    public boolean supports(String polyglotLanguageId) {
        return polyglotLanguageId != null && availableLanguageIds().contains(polyglotLanguageId);
    }

    /**
     * Polyglot id for languages the embedded engine can serve.
     *
     * <p>Java is deliberately absent: Java programs are compiled with the JDK
     * compiler and run in a child class loader, not as a Truffle language. C,
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
