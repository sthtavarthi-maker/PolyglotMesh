package com.polyglotmesh.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Languages the product targets, mirroring the nine editor modes in the frontend.
 *
 * <p>Being listed here only means the API recognises the identifier. Execution
 * support is a separate concern: an engine is available only when a
 * {@link com.polyglotmesh.executor.CodeExecutor} for the language has been
 * registered. Until then the execution endpoint answers 501 for the language.</p>
 */
public enum Language {

    JAVA("java", "Java"),
    PYTHON("python", "Python"),
    JAVASCRIPT("javascript", "JavaScript"),
    RUBY("ruby", "Ruby"),
    C("c", "C"),
    CPP("cpp", "C++"),
    RUST("rust", "Rust"),
    GO("go", "Go"),
    R("r", "R");

    private final String id;
    private final String displayName;

    Language(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** Identifier used by the REST API and the frontend, e.g. {@code javascript}. */
    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * Resolves a language id case-insensitively, trimming surrounding space.
     *
     * @return the matching language, or empty for an unknown identifier
     */
    public static Optional<Language> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(language -> language.id.equals(normalized))
                .findFirst();
    }

    /** All known ids, in declaration order, used in error messages. */
    public static List<String> knownIds() {
        return Arrays.stream(values()).map(Language::id).toList();
    }
}
