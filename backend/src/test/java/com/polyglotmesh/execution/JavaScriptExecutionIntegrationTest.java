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
 * End-to-end check of the running application: JavaScript is executed by the
 * real GraalJS engine, while languages without an adapter still report 501.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JavaScriptExecutionIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void executesJavaScriptThroughTheApi() {
        ResponseEntity<JsonNode> response = execute(Map.of(
                "language", "javascript",
                "code", "console.log('Hello PolyglotMesh');",
                "input", ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("language").asText()).isEqualTo("javascript");
        assertThat(body.path("output").asText()).isEqualTo("Hello PolyglotMesh\n");
        assertThat(body.path("stderr").asText()).isEmpty();
        assertThat(body.path("error").isNull()).isTrue();
        assertThat(body.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(body.path("executionTimeMs").asLong()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void passesStandardInputToJavaScript() {
        ResponseEntity<JsonNode> response = execute(Map.of(
                "language", "javascript",
                "code", "console.log(readLine().toUpperCase());",
                "input", "hello"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("output").asText()).isEqualTo("HELLO\n");
    }

    @Test
    void separatesStandardOutputAndStandardError() {
        ResponseEntity<JsonNode> response = execute(Map.of(
                "language", "javascript",
                "code", "console.log('out'); console.error('err');",
                "input", ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("output").asText()).isEqualTo("out\n");
        assertThat(body.path("stderr").asText()).isEqualTo("err\n");
        assertThat(body.path("status").asText()).isEqualTo("SUCCESS");
    }

    @Test
    void reportsJavaScriptFailuresWithoutAnHttpError() {
        ResponseEntity<JsonNode> response = execute(Map.of(
                "language", "javascript",
                "code", "throw new Error('boom');",
                "input", ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
        assertThat(response.getBody().path("error").asText()).contains("boom");
        assertThat(response.getBody().path("status").asText()).isEqualTo("RUNTIME_ERROR");
    }

    @Test
    void languagesWithoutAnEngineStillAnswer501() {
        ResponseEntity<JsonNode> response = execute(Map.of(
                "language", "ruby",
                "code", "puts 'hello'",
                "input", ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
        assertThat(response.getBody().path("status").asInt()).isEqualTo(501);
        assertThat(response.getBody().path("message").asText()).contains("ruby");
    }

    private ResponseEntity<JsonNode> execute(Map<String, String> body) {
        return restTemplate.postForEntity("/api/execute", body, JsonNode.class);
    }
}
