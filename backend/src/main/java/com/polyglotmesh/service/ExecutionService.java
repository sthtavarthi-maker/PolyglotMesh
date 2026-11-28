package com.polyglotmesh.service;

import com.polyglotmesh.dto.ExecutionRequest;
import com.polyglotmesh.dto.ExecutionResponse;
import com.polyglotmesh.dto.LanguageStatus;
import com.polyglotmesh.exception.ExecutionUnavailableException;
import com.polyglotmesh.exception.UnsupportedLanguageException;
import com.polyglotmesh.executor.CodeExecutor;
import com.polyglotmesh.model.ExecutionResult;
import com.polyglotmesh.model.Language;
import com.polyglotmesh.observability.ExecutionTelemetry;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Language resolution and executor routing.
 *
 * <p>The service owns no execution logic. Executors are injected as a list and
 * indexed by language, so a request is answered by exactly one adapter. A known
 * language with no registered adapter fails with
 * {@link ExecutionUnavailableException} instead of returning invented output.</p>
 *
 * <p>Every completed execution is handed to {@link ExecutionTelemetry}; the
 * telemetry layer owns metrics and the structured execution log, keeping this
 * class focused on routing.</p>
 */
@Service
public class ExecutionService {

    private final Map<Language, CodeExecutor> executors;
    private final ExecutionTelemetry telemetry;

    public ExecutionService(List<CodeExecutor> executors, ExecutionTelemetry telemetry) {
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
        this.telemetry = telemetry;
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
        telemetry.record(language, result);
        return ExecutionResponse.from(language, result);
    }

    /** Languages that currently have an available executor, in enum order. */
    public List<Language> availableLanguages() {
        return executors.values().stream()
                .filter(CodeExecutor::isAvailable)
                .map(CodeExecutor::language)
                .sorted()
                .toList();
    }

    /**
     * Every product language with its current availability, in enum order.
     * Unavailable languages are included so clients can render them disabled
     * instead of probing with real execution requests.
     */
    public List<LanguageStatus> languageStatuses() {
        List<LanguageStatus> statuses = new ArrayList<>(Language.values().length);
        for (Language language : Language.values()) {
            CodeExecutor executor = executors.get(language);
            boolean available = executor != null && executor.isAvailable();
            statuses.add(new LanguageStatus(language.id(), language.displayName(), available));
        }
        return List.copyOf(statuses);
    }
}
