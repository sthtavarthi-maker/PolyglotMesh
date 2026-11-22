package com.polyglotmesh.executor;

import com.polyglotmesh.dto.ExecutionRequest;
import com.polyglotmesh.model.ExecutionResult;
import com.polyglotmesh.model.Language;

/**
 * Contract every language adapter implements.
 *
 * <p>Executors are discovered by Spring from this interface, so adding a
 * language means adding one bean: no controller or service change is needed.
 * Implementations must do real work — the service never fabricates a result.</p>
 */
public interface CodeExecutor {

    /** Language this executor serves. Must be unique across executors. */
    Language language();

    /**
     * Whether the backing runtime or toolchain is usable on this host right
     * now. A registered but unavailable executor produces 501, never a fake
     * result. JavaScript, Python and Java implement real checks.
     */
    boolean isAvailable();

    /**
     * Runs one request. Implementations are responsible for enforcing their own
     * timeout and for measuring {@code executionTimeMs}; validation and routing
     * happen in {@link com.polyglotmesh.service.ExecutionService}.
     */
    ExecutionResult execute(ExecutionRequest request);
}
