package com.polyglotmesh.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end observability contract: every response carries a correlation id,
 * a caller-supplied id is echoed when safe, and completed executions are
 * visible as counters, timers and the read-only metrics endpoint.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "polyglotmesh.graalvm.setup-timeout=180s",
            "polyglotmesh.execution.rate-limit-enabled=false"
        })
class ObservabilityIntegrationTest {

    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void successfulExecutionCarriesARequestIdAndIsCounted() {
        ResponseEntity<JsonNode> response =
                execute("javascript", "console.log('observe');", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER))
                .matches(UUID_PATTERN);

        Counter counter = meterRegistry.get(ExecutionTelemetry.EXECUTIONS_METRIC)
                .tags("language", "javascript", "status", "SUCCESS")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isGreaterThanOrEqualTo(1.0);

        Timer timer = meterRegistry.get(ExecutionTelemetry.DURATION_METRIC)
                .tags("language", "javascript", "status", "SUCCESS")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isGreaterThanOrEqualTo(1);

        ResponseEntity<JsonNode> metrics = restTemplate.getForEntity(
                "/actuator/metrics/" + ExecutionTelemetry.EXECUTIONS_METRIC, JsonNode.class);
        assertThat(metrics.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(metrics.getBody().path("name").asText())
                .isEqualTo(ExecutionTelemetry.EXECUTIONS_METRIC);
    }

    @Test
    void echoesAWellFormedSuppliedRequestId() {
        ResponseEntity<JsonNode> response =
                execute("javascript", "console.log('trace');", "trace-12345678");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER))
                .isEqualTo("trace-12345678");
    }

    @Test
    void replacesAnUnsafeSuppliedRequestId() {
        ResponseEntity<JsonNode> response =
                execute("javascript", "console.log('trace');", "not a valid id");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER))
                .isNotEqualTo("not a valid id")
                .matches(UUID_PATTERN);
    }

    @Test
    void failedExecutionIsCountedWithItsOutcome() {
        ResponseEntity<JsonNode> response =
                execute("javascript", "throw new Error('boom');", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isFalse();

        Counter counter = meterRegistry.get(ExecutionTelemetry.EXECUTIONS_METRIC)
                .tags("language", "javascript", "status", "RUNTIME_ERROR")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isGreaterThanOrEqualTo(1.0);
    }

    private ResponseEntity<JsonNode> execute(String language, String code, String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (requestId != null) {
            headers.set(RequestIdFilter.REQUEST_ID_HEADER, requestId);
        }
        Map<String, String> body = Map.of("language", language, "code", code, "input", "");
        return restTemplate.exchange(
                "/api/execute",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                JsonNode.class);
    }
}
