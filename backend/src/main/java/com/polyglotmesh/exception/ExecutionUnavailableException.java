package com.polyglotmesh.exception;

import com.polyglotmesh.model.Language;

/**
 * Raised when a known language has no execution engine registered yet.
 *
 * <p>Week 2 starts with no engines at all, so every valid request gets this
 * response until the JavaScript, Python and Java adapters are added. This is a
 * deliberate "not available" answer, not simulated execution.</p>
 */
public class ExecutionUnavailableException extends RuntimeException {

    private final Language language;

    public ExecutionUnavailableException(Language language) {
        super("No execution engine is available for '" + language.id() + "' yet.");
        this.language = language;
    }

    public Language language() {
        return language;
    }
}
