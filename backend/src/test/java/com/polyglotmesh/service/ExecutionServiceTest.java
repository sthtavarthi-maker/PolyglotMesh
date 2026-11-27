package com.polyglotmesh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.polyglotmesh.dto.ExecutionRequest;
import com.polyglotmesh.dto.ExecutionResponse;
import com.polyglotmesh.dto.LanguageStatus;
import com.polyglotmesh.exception.ExecutionUnavailableException;
import com.polyglotmesh.exception.UnsupportedLanguageException;
import com.polyglotmesh.executor.CodeExecutor;
import com.polyglotmesh.model.ExecutionResult;
import com.polyglotmesh.model.ExecutionStatus;
import com.polyglotmesh.model.Language;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecutionServiceTest {

    @Test
    void routesRequestToRegisteredExecutor() {
        CodeExecutor executor = executorFor(Language.JAVASCRIPT);
        ExecutionRequest request = request("javascript");
        when(executor.isAvailable()).thenReturn(true);
        when(executor.execute(request))
                .thenReturn(ExecutionResult.success("Hello", "warning\n", 12));

        ExecutionService service = new ExecutionService(List.of(executor));
        ExecutionResponse response = service.execute(request);

        assertThat(response.success()).isTrue();
        assertThat(response.language()).isEqualTo("javascript");
        assertThat(response.output()).isEqualTo("Hello");
        assertThat(response.stderr()).isEqualTo("warning\n");
        assertThat(response.error()).isNull();
        assertThat(response.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(response.executionTimeMs()).isEqualTo(12);
        verify(executor).execute(request);
    }

    @Test
    void mapsFailedExecutionWithoutThrowing() {
        CodeExecutor executor = executorFor(Language.PYTHON);
        ExecutionRequest request = request("python");
        when(executor.isAvailable()).thenReturn(true);
        when(executor.execute(request)).thenReturn(ExecutionResult.failure(
                ExecutionStatus.SYNTAX_ERROR, "", "", "SyntaxError", 3));

        ExecutionService service = new ExecutionService(List.of(executor));
        ExecutionResponse response = service.execute(request);

        assertThat(response.success()).isFalse();
        assertThat(response.language()).isEqualTo("python");
        assertThat(response.output()).isEmpty();
        assertThat(response.stderr()).isEmpty();
        assertThat(response.error()).isEqualTo("SyntaxError");
        assertThat(response.status()).isEqualTo(ExecutionStatus.SYNTAX_ERROR);
        assertThat(response.executionTimeMs()).isEqualTo(3);
    }

    @Test
    void resolvesLanguageCaseInsensitively() {
        CodeExecutor executor = executorFor(Language.JAVASCRIPT);
        ExecutionRequest request = request("  JavaScript ");
        when(executor.isAvailable()).thenReturn(true);
        when(executor.execute(request)).thenReturn(ExecutionResult.success("ok", 1));

        ExecutionService service = new ExecutionService(List.of(executor));
        ExecutionResponse response = service.execute(request);

        assertThat(response.success()).isTrue();
        assertThat(response.language()).isEqualTo("javascript");
    }

    @Test
    void rejectsUnknownLanguage() {
        ExecutionService service = new ExecutionService(List.of());

        assertThatThrownBy(() -> service.execute(request("cobol")))
                .isInstanceOf(UnsupportedLanguageException.class)
                .hasMessageContaining("cobol")
                .hasMessageContaining("javascript");
    }

    @Test
    void reportsUnavailableWhenNoExecutorIsRegistered() {
        ExecutionService service = new ExecutionService(List.of());

        assertThatThrownBy(() -> service.execute(request("ruby")))
                .isInstanceOf(ExecutionUnavailableException.class)
                .hasMessageContaining("ruby");
    }

    @Test
    void reportsUnavailableWhenOnlyAnotherLanguageHasAnExecutor() {
        ExecutionService service = new ExecutionService(List.of(executorFor(Language.JAVASCRIPT)));

        assertThatThrownBy(() -> service.execute(request("python")))
                .isInstanceOf(ExecutionUnavailableException.class)
                .hasMessageContaining("python");
    }

    @Test
    void reportsUnavailableWhenRegisteredExecutorIsUnavailable() {
        CodeExecutor executor = executorFor(Language.PYTHON);
        when(executor.isAvailable()).thenReturn(false);
        ExecutionService service = new ExecutionService(List.of(executor));

        assertThatThrownBy(() -> service.execute(request("python")))
                .isInstanceOf(ExecutionUnavailableException.class)
                .hasMessageContaining("python");
    }

    @Test
    void rejectsDuplicateExecutorsForOneLanguage() {
        CodeExecutor first = executorFor(Language.JAVASCRIPT);
        CodeExecutor second = executorFor(Language.JAVASCRIPT);

        assertThatThrownBy(() -> new ExecutionService(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("javascript");
    }

    @Test
    void listsOnlyAvailableLanguages() {
        CodeExecutor available = executorFor(Language.JAVA);
        when(available.isAvailable()).thenReturn(true);
        CodeExecutor unavailable = executorFor(Language.PYTHON);
        when(unavailable.isAvailable()).thenReturn(false);
        ExecutionService service = new ExecutionService(List.of(available, unavailable));

        assertThat(service.availableLanguages()).containsExactly(Language.JAVA);
    }

    @Test
    void reportsEveryLanguageWithItsAvailability() {
        CodeExecutor available = executorFor(Language.JAVA);
        when(available.isAvailable()).thenReturn(true);
        ExecutionService service = new ExecutionService(List.of(available));

        List<LanguageStatus> statuses = service.languageStatuses();

        assertThat(statuses).hasSize(Language.values().length);
        assertThat(statuses).extracting(LanguageStatus::id)
                .containsExactlyElementsOf(Language.knownIds());
        assertThat(statuses).filteredOn(status -> status.id().equals("java"))
                .allMatch(LanguageStatus::available);
        assertThat(statuses).filteredOn(status -> status.id().equals("ruby"))
                .noneMatch(LanguageStatus::available);
        assertThat(statuses).filteredOn(status -> status.id().equals("javascript"))
                .allMatch(status -> status.displayName().equals("JavaScript") && !status.available());
    }

    private CodeExecutor executorFor(Language language) {
        CodeExecutor executor = mock(CodeExecutor.class);
        when(executor.language()).thenReturn(language);
        return executor;
    }

    private ExecutionRequest request(String language) {
        return new ExecutionRequest(language, "code", "");
    }
}
