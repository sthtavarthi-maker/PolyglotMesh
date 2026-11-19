package com.polyglotmesh.service;

import com.polyglotmesh.dto.ExecutionRequest;
import com.polyglotmesh.dto.ExecutionResponse;
import com.polyglotmesh.exception.ExecutionUnavailableException;
import com.polyglotmesh.exception.UnsupportedLanguageException;
import com.polyglotmesh.executor.CodeExecutor;
import com.polyglotmesh.model.ExecutionResult;
import com.polyglotmesh.model.Language;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Language resolution and executor routing.
 *
 * <p>The service owns no execution logic. Executors are injected as a list and
 * indexed by language, so a request is answered by exactly one adapter. With no
 * adapter registered (the state at Day 9) a known language fails with
 * {@link ExecutionUnavailableException} instead of returning invented output.</p>
 */
@Service
public class ExecutionService {

    private final Map<Language, CodeExecutor> executors;

    public ExecutionService(List<CodeExecutor> executors) {
        Map<Language, CodeExecutor> indexed = new EnumMap<>(Language.class);
        for (CodeExecutor executor : executors) {
            CodeExecutor existing = indexed.putIfAbsent(executor.language(), executor);
            if (existing != null) {
                throw new IllegalStateException("Multiple executors registered for language '"
                        + executor.language().id() + "': "
                        + existing.getClass().getName() + " and "
                        + executor.getClass().getName());
            }
        }
        this.executors = Map.copyOf(indexed);
    }

    /**
     * Validates the language, selects its executor and maps the internal result
     * to the API response.
     *
     * @throws UnsupportedLanguageException  when the id is unknown (400)
     * @throws ExecutionUnavailableException when no engine is registered (501)
     */
    public ExecutionResponse execute(ExecutionRequest request) {
        Language language = Language.fromId(request.language())
                .orElseThrow(() -> new UnsupportedLanguageException(request.language()));

        CodeExecutor executor = executors.get(language);
        if (executor == null || !executor.isAvailable()) {
            throw new ExecutionUnavailableException(language);
        }

        ExecutionResult result = executor.execute(request);
        return ExecutionResponse.from(language, result);
    }

    /** Languages that currently have a real execution engine. */
    public List<Language> availableLanguages() {
        return List.copyOf(executors.keySet());
    }
}
