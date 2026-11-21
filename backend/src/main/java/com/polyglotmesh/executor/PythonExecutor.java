package com.polyglotmesh.executor;

import com.polyglotmesh.dto.ExecutionRequest;
import com.polyglotmesh.model.ExecutionResult;
import com.polyglotmesh.model.Language;
import com.polyglotmesh.runtime.GraalVmEngine;
import com.polyglotmesh.runtime.GraalVmSandbox;
import org.springframework.stereotype.Component;

/**
 * Python adapter backed by GraalPy.
 *
 * <p>Standard input is a real {@code sys.stdin}, so the built-in
 * {@code input()} behaves as Python programmers expect. Availability comes from
 * the engine, which reports {@code python} only when the GraalPy artifacts are
 * on the classpath.</p>
 */
@Component
public class PythonExecutor implements CodeExecutor {

    static final String POLYGLOT_LANGUAGE = "python";

    private final GraalVmEngine engine;
    private final GraalVmSandbox sandbox;

    public PythonExecutor(GraalVmEngine engine, GraalVmSandbox sandbox) {
        this.engine = engine;
        this.sandbox = sandbox;
    }

    @Override
    public Language language() {
        return Language.PYTHON;
    }

    @Override
    public boolean isAvailable() {
        return engine.supports(POLYGLOT_LANGUAGE);
    }

    @Override
    public ExecutionResult execute(ExecutionRequest request) {
        return sandbox.execute(POLYGLOT_LANGUAGE, request.code(), request.inputOrEmpty());
    }
}
