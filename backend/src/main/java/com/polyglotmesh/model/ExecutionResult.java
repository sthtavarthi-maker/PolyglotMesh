package com.polyglotmesh.model;

/**
 * Outcome of one execution, as produced by an executor.
 *
 * <p>This is the internal model; the controller returns
 * {@link com.polyglotmesh.dto.ExecutionResponse} instead. Keeping them separate
 * means the API contract can change without touching executor internals.</p>
 *
 * @param success          whether the program completed without errors
 * @param output           captured standard output, empty when there was none
 * @param error            compilation/runtime error text, null on success
 * @param executionTimeMs  measured execution time in milliseconds
 */
public record ExecutionResult(
        boolean success,
        String output,
        String error,
        long executionTimeMs) {

    public ExecutionResult {
        if (executionTimeMs < 0) {
            throw new IllegalArgumentException("executionTimeMs must not be negative");
        }
        output = output == null ? "" : output;
    }

    public static ExecutionResult success(String output, long executionTimeMs) {
        return new ExecutionResult(true, output, null, executionTimeMs);
    }

    public static ExecutionResult failure(String error, long executionTimeMs) {
        return new ExecutionResult(false, "", error, executionTimeMs);
    }
}
