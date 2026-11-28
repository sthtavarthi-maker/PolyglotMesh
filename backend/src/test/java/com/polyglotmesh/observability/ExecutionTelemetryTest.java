package com.polyglotmesh.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.polyglotmesh.model.ExecutionResult;
import com.polyglotmesh.model.ExecutionStatus;
import com.polyglotmesh.model.Language;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Telemetry must count by language and outcome, record the reported duration,
 * and log only metadata — never source, stdin or program output.
 */
class ExecutionTelemetryTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ExecutionTelemetry telemetry = new ExecutionTelemetry(registry);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void countsExecutionsAndRecordsDurationByLanguageAndStatus() {
        telemetry.record(Language.JAVASCRIPT, ExecutionResult.success("out\n", "", 12));
        telemetry.record(Language.JAVASCRIPT, ExecutionResult.success("out\n", "", 8));
        telemetry.record(Language.PYTHON, ExecutionResult.failure(
                ExecutionStatus.RUNTIME_ERROR, "", "", "boom", 5));

        Counter success = registry.get(ExecutionTelemetry.EXECUTIONS_METRIC)
                .tags("language", "javascript", "status", "SUCCESS")
                .counter();
        Counter failure = registry.get(ExecutionTelemetry.EXECUTIONS_METRIC)
                .tags("language", "python", "status", "RUNTIME_ERROR")
                .counter();

        assertThat(success).isNotNull();
        assertThat(success.count()).isEqualTo(2.0);
        assertThat(failure).isNotNull();
        assertThat(failure.count()).isEqualTo(1.0);

        Timer timer = registry.get(ExecutionTelemetry.DURATION_METRIC)
                .tags("language", "javascript", "status", "SUCCESS")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(2);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(20.0);
    }

    @Test
    void logsMetadataAndRequestIdButNotProgramText() {
        Logger logger = (Logger) LoggerFactory.getLogger(ExecutionTelemetry.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MDC.put(RequestIdFilter.MDC_KEY, "req-abcdef12");
            telemetry.record(Language.JAVA, ExecutionResult.failure(
                    ExecutionStatus.COMPILE_ERROR,
                    "printed output",
                    "standard error text",
                    "line 3: ';' expected",
                    7));
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).hasSize(1);
        String message = appender.list.get(0).getFormattedMessage();
        assertThat(message)
                .contains("requestId=req-abcdef12")
                .contains("language=java")
                .contains("status=COMPILE_ERROR")
                .contains("success=false")
                .contains("durationMs=7")
                .doesNotContain("printed output")
                .doesNotContain("standard error text")
                .doesNotContain("';' expected");
    }

    @Test
    void usesADashWhenNoRequestIdIsAvailable() {
        Logger logger = (Logger) LoggerFactory.getLogger(ExecutionTelemetry.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            telemetry.record(Language.JAVASCRIPT, ExecutionResult.success("out", 3));
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("requestId=-");
    }
}
