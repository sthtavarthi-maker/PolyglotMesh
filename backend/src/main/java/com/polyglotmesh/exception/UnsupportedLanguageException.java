package com.polyglotmesh.exception;

import com.polyglotmesh.model.Language;

/**
 * Raised when the request names a language the API does not know at all.
 *
 * <p>Known-but-not-yet-executable languages are a different case: they resolve
 * to a {@link Language} and fail in the service with
 * {@link ExecutionUnavailableException} (501).</p>
 */
public class UnsupportedLanguageException extends RuntimeException {

    private final String language;

    public UnsupportedLanguageException(String language) {
        super("Unknown language '" + language + "'. Known languages: "
                + String.join(", ", Language.knownIds()));
        this.language = language;
    }

    public String language() {
        return language;
    }
}
