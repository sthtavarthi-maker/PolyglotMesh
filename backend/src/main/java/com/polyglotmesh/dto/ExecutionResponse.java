package com.polyglotmesh.dto;

import com.polyglotmesh.model.ExecutionResult;
import com.polyglotmesh.model.ExecutionStatus;
import com.polyglotmesh.model.Language;

/**
 * Response body of {@code POST /api/execute}.
 *
 * <p>Field order is the documented API order: success, language, output, error,
 * stderr, status, executionTimeMs. {@code output} is standard output only and
 * {@code stderr} is standard error only. {@code error} carries the diagnostic
 * (compiler output, exception message or timeout notice) and is {@code null}
 * on success. Null fields are serialised on purpose so clients can rely on a
 * stable shape.</p>
 */
public record ExecutionResponse(
        boolean success,
        String language,
        String output,
        String error,
        String stderr,
        ExecutionStatus status,
        long executionTimeMs) {

    public static ExecutionResponse from(Language language, ExecutionResult result) {
        return new ExecutionResponse(
                result.success(),
                language.id(),
                result.output(),
                result.error(),
                result.stderr(),
                result.status(),
                result.executionTimeMs());
    }
}
