package com.polyglotmesh.model;

/**
 * Classification of one execution outcome.
 *
 * <p>The API returns this as the {@code status} field so a client can tell the
 * difference between a program that compiled and ran, a program the compiler
 * rejected, a syntax error, a runtime exception, a timeout and a server-side
 * failure. It is deliberately separate from the HTTP status: a program that
 * fails still produced a valid execution result and is returned with HTTP 200.</p>
 */
public enum ExecutionStatus {

    /** The program compiled (when applicable) and exited successfully. */
    SUCCESS,

    /** The compiler rejected the source; {@code error} carries its diagnostics. */
    COMPILE_ERROR,

    /** The interpreter rejected the source before running it. */
    SYNTAX_ERROR,

    /** The program started but raised an exception or exited non-zero. */
    RUNTIME_ERROR,

    /** A setup, compilation or execution budget was exceeded. */
    TIMEOUT,

    /** The request was interrupted or the execution service is shutting down. */
    CANCELLED,

    /** The server failed for a reason that is not the program's fault. */
    INTERNAL_ERROR
}
