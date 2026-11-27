package com.polyglotmesh.execution;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end verification of the Day 17 diagnostics contract: every executed
 * response carries a classification, stdout and stderr are separate, and
 * API-level failures keep their existing HTTP status codes and error shape.
 *
 * <p>The setup budget is raised for this class because it pays the one-off
 * GraalPy context start; under a loaded test JVM that can exceed the 60 s
 * production default, which would turn a diagnostics assertion into a
 * setup-timeout result.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "polyglotmesh.graalvm.setup-timeout=180s",
            "polyglotmesh.execution.rate-limit-enabled=false"
        })
class ExecutionDiagnosticsIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void classifiesJavaScriptSyntaxErrors() {
        JsonNode body = execute("javascript", "console.log(");

        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("status").asText()).isEqualTo("SYNTAX_ERROR");
        assertThat(body.path("error").asText()).containsIgnoringCase("syntax");
    }

    @Test
    void classifiesPythonSyntaxErrors() {
        JsonNode body = execute("python", "def broken(:");

        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("status").asText()).isEqualTo("SYNTAX_ERROR");
        assertThat(body.path("error").asText()).containsIgnoringCase("syntax");
    }

    @Test
    void classifiesJavaRuntimeExceptionsAndPreservesStdout() {
        JsonNode body = execute("java", """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("before");
                        int value = 1 / 0;
                    }
                }
                """);

        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("status").asText()).isEqualTo("RUNTIME_ERROR");
        assertThat(body.path("output").asText()).isEqualTo("before\n");
        assertThat(body.path("stderr").asText()).contains("ArithmeticException");
        assertThat(body.path("error").asText()).contains("ArithmeticException");
        assertThat(body.path("error").asText()).doesNotContain("com.polyglotmesh");
    }

    @Test
    void classifiesJavaSourceWithoutATypeAsACompileError() {
        JsonNode body = execute("java", "System.out.println(\"hi\");");

        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("status").asText()).isEqualTo("COMPILE_ERROR");
        assertThat(body.path("error").asText()).contains("No top-level");
    }

    @Test
    void keepsCompilerDiagnosticsWithLineNumbers() {
        JsonNode body = execute("java", """
                public class Main {
                    public static void main(String[] args) {
                        int value = ;
                    }
                }
                """);

        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("status").asText()).isEqualTo("COMPILE_ERROR");
        assertThat(body.path("error").asText()).contains("line 3");
    }

    @Test
    void doesNotLeakBackendDetailsInExecutionErrors() {
        JsonNode body = execute("javascript", "throw new Error('boom');");

        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("status").asText()).isEqualTo("RUNTIME_ERROR");
        assertThat(body.path("error").asText()).contains("boom");
        assertThat(body.path("error").asText())
                .doesNotContain("com.polyglotmesh")
                .doesNotContain("GraalVmSandbox")
                .doesNotContain("java.lang.reflect");
    }

    @Test
    void unknownLanguageKeepsThe400ApiErrorContract() {
        ResponseEntity<JsonNode> response = post(
                Map.of("language", "cobol", "code", "DISPLAY 'HI'", "input", ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = response.getBody();
        assertThat(body.path("status").asInt()).isEqualTo(400);
        assertThat(body.path("message").asText()).contains("Unknown language 'cobol'");
        assertThat(body.has("success")).isFalse();
        assertThat(body.has("language")).isFalse();
    }

    @Test
    void malformedJsonKeepsThe400ApiErrorContract() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "/api/execute",
                new HttpEntity<>("{\"language\":", headers),
                JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("status").asInt()).isEqualTo(400);
        assertThat(response.getBody().path("message").asText())
                .isEqualTo("Malformed or missing JSON request body.");
    }

    @Test
    void knownLanguageWithoutARuntimeKeeps501() {
        ResponseEntity<JsonNode> response = post(
                Map.of("language", "ruby", "code", "puts 'hello'", "input", ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
        assertThat(response.getBody().path("status").asInt()).isEqualTo(501);
        assertThat(response.getBody().path("message").asText()).contains("ruby");
        assertThat(response.getBody().has("success")).isFalse();
    }

    private JsonNode execute(String language, String code) {
        ResponseEntity<JsonNode> response = post(
                Map.of("language", language, "code", code, "input", ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("language").asText()).isEqualTo(language);
        assertThat(body.path("executionTimeMs").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(body.path("status").isTextual()).isTrue();
        return body;
    }

    private ResponseEntity<JsonNode> post(Map<String, String> body) {
        return restTemplate.postForEntity("/api/execute", body, JsonNode.class);
    }
}
