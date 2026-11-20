package com.polyglotmesh.executor;

import com.polyglotmesh.dto.ExecutionRequest;
import com.polyglotmesh.model.ExecutionResult;
import com.polyglotmesh.model.Language;
import com.polyglotmesh.runtime.GraalVmEngine;
import com.polyglotmesh.runtime.GraalVmSandbox;
import org.springframework.stereotype.Component;

/**
 * JavaScript adapter backed by GraalJS.
 *
 * <p>Availability is decided by the engine, not by configuration: if the
 * GraalJS artifacts are missing the executor reports unavailable and the API
 * answers 501 rather than pretending to run the program.</p>
 */
@Component
public class JavaScriptExecutor implements CodeExecutor {

    static final String POLYGLOT_LANGUAGE = "js";

    private final GraalVmEngine engine;
    private final GraalVmSandbox sandbox;

    public JavaScriptExecutor(GraalVmEngine engine, GraalVmSandbox sandbox) {
        this.engine = engine;
        this.sandbox = sandbox;
    }

    @Override
    public Language language() {
        return Language.JAVASCRIPT;
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
