package com.polyglotmesh.dto;

import com.polyglotmesh.model.ExecutionResult;
import com.polyglotmesh.model.Language;

/**
 * Response body of {@code POST /api/execute}.
 *
 * <p>Field order is the documented API order: success, language, output, error,
 * executionTimeMs. Null fields are serialised on purpose so clients can rely on
 * a stable shape.</p>
 */
public record ExecutionResponse(
        boolean success,
        String language,
        String output,
        String error,
        long executionTimeMs) {

    public static ExecutionResponse from(Language language, ExecutionResult result) {
        return new ExecutionResponse(
                result.success(),
                language.id(),
                result.output(),
                result.error(),
                result.executionTimeMs());
    }
}
