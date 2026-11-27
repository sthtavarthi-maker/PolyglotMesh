package com.polyglotmesh.model;

/**
 * Outcome of one execution, as produced by an executor.
 *
 * <p>This is the internal model; the controller returns
 * {@link com.polyglotmesh.dto.ExecutionResponse} instead. Keeping them separate
 * means the API contract can change without touching executor internals.</p>
 *
 * <p>Standard output and standard error are captured separately. A program may
 * write to stderr and still succeed, so {@code stderr} being non-empty does not
 * imply failure; {@link #success()} and {@link #status()} describe the outcome.
 * {@code error} is the human-readable diagnostic (compiler output, exception
 * message or timeout notice) and is {@code null} on success.</p>
 *
 * @param success          whether the program completed without errors
 * @param output           captured standard output, empty when there was none
 * @param stderr           captured standard error, empty when there was none
 * @param error            compilation/runtime error text, null on success
 * @param status           outcome classification, never null
 * @param executionTimeMs  measured execution time in milliseconds
 */
public record ExecutionResult(
        boolean success,
        String output,
        String stderr,
        String error,
        ExecutionStatus status,
        long executionTimeMs) {

    public ExecutionResult {
        if (executionTimeMs < 0) {
            throw new IllegalArgumentException("executionTimeMs must not be negative");
        }
        output = output == null ? "" : output;
        stderr = stderr == null ? "" : stderr;
        if (status == null) {
            status = success ? ExecutionStatus.SUCCESS : ExecutionStatus.RUNTIME_ERROR;
        }
        if (success && status != ExecutionStatus.SUCCESS) {
            throw new IllegalArgumentException(
                    "A successful execution must have SUCCESS status, not " + status);
        }
        if (!success && status == ExecutionStatus.SUCCESS) {
            throw new IllegalArgumentException(
                    "A failed execution must not have SUCCESS status");
        }
    }

    public static ExecutionResult success(String output, String stderr, long executionTimeMs) {
        return new ExecutionResult(
                true, output, stderr, null, ExecutionStatus.SUCCESS, executionTimeMs);
    }

    public static ExecutionResult success(String output, long executionTimeMs) {
        return success(output, "", executionTimeMs);
    }

    /** Failed execution with separate stdout/stderr and an explicit classification. */
    public static ExecutionResult failure(
            ExecutionStatus status,
            String output,
            String stderr,
            String error,
            long executionTimeMs) {
        return new ExecutionResult(false, output, stderr, error, status, executionTimeMs);
    }

    public static ExecutionResult failure(String error, long executionTimeMs) {
        return failure(ExecutionStatus.RUNTIME_ERROR, "", "", error, executionTimeMs);
    }

    /** Failure that still returns whatever the program printed before it stopped. */
    public static ExecutionResult failure(String output, String error, long executionTimeMs) {
        return failure(ExecutionStatus.RUNTIME_ERROR, output, "", error, executionTimeMs);
    }
}
