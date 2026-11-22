package com.polyglotmesh.executor;

import com.polyglotmesh.dto.ExecutionRequest;
import com.polyglotmesh.model.ExecutionResult;
import com.polyglotmesh.model.Language;
import com.polyglotmesh.runtime.JavaExecutionSandbox;
import org.springframework.stereotype.Component;

/**
 * Java adapter backed by the JDK toolchain.
 *
 * <p>Unlike the GraalVM adapters, Java needs no extra dependency: the JDK
 * already ships the compiler and the launcher. Availability is checked at
 * runtime, so a JRE without {@code javax.tools} answers 501 instead of failing
 * with a server error.</p>
 */
@Component
public class JavaExecutor implements CodeExecutor {

    private final JavaExecutionSandbox sandbox;

    public JavaExecutor(JavaExecutionSandbox sandbox) {
        this.sandbox = sandbox;
    }

    @Override
    public Language language() {
        return Language.JAVA;
    }

    @Override
    public boolean isAvailable() {
        return sandbox.isAvailable();
    }

    @Override
    public ExecutionResult execute(ExecutionRequest request) {
        return sandbox.execute(request.code(), request.inputOrEmpty());
    }
}
