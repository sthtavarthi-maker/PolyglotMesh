package com.polyglotmesh.observability;

import com.polyglotmesh.model.ExecutionResult;
import com.polyglotmesh.model.Language;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Records one metric and one log line per completed execution.
 *
 * <p>Metrics are tagged by language and outcome so dashboards can separate a
 * slow language from a failing one. The log line carries the same fields plus
 * the request id from {@link RequestIdFilter}; source code, standard input and
 * program output are deliberately never logged.</p>
 */
@Component
public class ExecutionTelemetry {

    public static final String EXECUTIONS_METRIC = "polyglotmesh.executions";
    public static final String DURATION_METRIC = "polyglotmesh.execution.duration";

    private static final Logger log = LoggerFactory.getLogger(ExecutionTelemetry.class);

    private final MeterRegistry registry;

    public ExecutionTelemetry(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Records a finished execution; the result must not contain source text. */
    public void record(Language language, ExecutionResult result) {
        String languageId = language.id();
        String status = result.status().name();

        Counter.builder(EXECUTIONS_METRIC)
                .description("Completed executions by language and outcome")
                .tags("language", languageId, "status", status)
                .register(registry)
                .increment();

        Timer.builder(DURATION_METRIC)
                .description("Execution time reported by the sandbox, by language and outcome")
                .tags("language", languageId, "status", status)
                .register(registry)
                .record(result.executionTimeMs(), TimeUnit.MILLISECONDS);

        log.info(
                "execution requestId={} language={} status={} success={} durationMs={}",
                currentRequestId(),
                languageId,
                status,
                result.success(),
                result.executionTimeMs());
    }

    private static String currentRequestId() {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        return requestId == null ? "-" : requestId;
    }
}
