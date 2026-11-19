package com.polyglotmesh.dto;

import java.time.Instant;

/**
 * Uniform error body returned by {@link com.polyglotmesh.exception.GlobalExceptionHandler}.
 *
 * <p>Contains no exception class names, stack traces or internal paths beyond
 * the request URI, so failed requests cannot leak implementation details.</p>
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path) {
}
