package com.polyglotmesh.execution;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Proves that a runaway program is stopped at the configured budget and that
 * the service still accepts and completes work afterwards. Java gets its own
 * run budget, separate from the compile budget.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "polyglotmesh.graalvm.execution-timeout=2s",
            "polyglotmesh.execution.java-execution-timeout=2s",
            "polyglotmesh.execution.rate-limit-enabled=false"
        })
class ExecutionTimeoutIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @Timeout(120)
    void javascriptTimeoutDoesNotAffectLaterExecutions() {
        JsonNode timedOut = execute("javascript", "while (true) {}");

        assertThat(timedOut.path("success").asBoolean()).isFalse();
        assertThat(timedOut.path("error").asText()).contains("timed out");
        assertThat(timedOut.path("status").asText()).isEqualTo("TIMEOUT");

        JsonNode recovered = execute("javascript", "console.log('after timeout');");

        assertThat(recovered.path("success").asBoolean()).isTrue();
        assertThat(recovered.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(recovered.path("output").asText()).isEqualTo("after timeout\n");
    }

    @Test
    @Timeout(180)
    void pythonTimeoutDoesNotAffectLaterExecutions() {
        JsonNode timedOut = execute("python", "while True:\n    pass");

        assertThat(timedOut.path("success").asBoolean()).isFalse();
        assertThat(timedOut.path("error").asText()).contains("timed out");
        assertThat(timedOut.path("status").asText()).isEqualTo("TIMEOUT");

        JsonNode recovered = execute("python", "print('after timeout')");

        assertThat(recovered.path("success").asBoolean()).isTrue();
        assertThat(recovered.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(recovered.path("output").asText()).isEqualTo("after timeout\n");
    }

    @Test
    @Timeout(180)
    void javaRunTimeoutDoesNotAffectLaterExecutions() {
        JsonNode timedOut = execute("java", """
                public class Main {
                    public static void main(String[] args) {
                        while (true) {
                            // spin
                        }
                    }
                }
                """);

        assertThat(timedOut.path("success").asBoolean()).isFalse();
        assertThat(timedOut.path("error").asText()).contains("timed out");
        assertThat(timedOut.path("status").asText()).isEqualTo("TIMEOUT");

        JsonNode recovered = execute("java", """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("after timeout");
                    }
                }
                """);

        assertThat(recovered.path("success").asBoolean()).isTrue();
        assertThat(recovered.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(recovered.path("output").asText()).isEqualTo("after timeout\n");
    }

    private JsonNode execute(String language, String code) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "/api/execute",
                Map.of("language", language, "code", code, "input", ""),
                JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("executionTimeMs").asLong()).isGreaterThanOrEqualTo(0);
        return body;
    }
}
