package com.polyglotmesh.execution;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end check that Python runs on GraalPy through the public API, with
 * standard input and error reporting.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PythonExecutionIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void executesPythonThroughTheApi() {
        ResponseEntity<JsonNode> response = execute(Map.of(
                "language", "python",
                "code", "print('Hello from Python')",
                "input", ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("language").asText()).isEqualTo("python");
        assertThat(body.path("output").asText()).isEqualTo("Hello from Python\n");
        assertThat(body.path("error").isNull()).isTrue();
    }

    @Test
    void passesStandardInputToPython() {
        ResponseEntity<JsonNode> response = execute(Map.of(
                "language", "python",
                "code", """
                        name = input()
                        count = int(input())
                        print(f"Hello, {name}! " * count)
                        """,
                "input", "Ada\n2\n"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("output").asText())
                .isEqualTo("Hello, Ada! Hello, Ada! \n");
    }

    @Test
    void reportsPythonFailuresWithoutAnHttpError() {
        ResponseEntity<JsonNode> response = execute(Map.of(
                "language", "python",
                "code", "print(1 / 0)",
                "input", ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
        assertThat(response.getBody().path("error").asText()).contains("ZeroDivisionError");
    }

    private ResponseEntity<JsonNode> execute(Map<String, String> body) {
        return restTemplate.postForEntity("/api/execute", body, JsonNode.class);
    }
}
