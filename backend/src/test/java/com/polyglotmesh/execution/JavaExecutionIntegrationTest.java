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
 * End-to-end check that Java is compiled and executed through the public API,
 * and that languages without a runtime still answer 501.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JavaExecutionIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void executesJavaThroughTheApi() {
        ResponseEntity<JsonNode> response = execute(Map.of(
                "language", "java",
                "code", """
                        public class Main {
                            public static void main(String[] args) {
                                System.out.println("Hello from Java");
                            }
                        }
                        """,
                "input", ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("language").asText()).isEqualTo("java");
        assertThat(body.path("output").asText()).isEqualTo("Hello from Java\n");
        assertThat(body.path("stderr").asText()).isEmpty();
        assertThat(body.path("error").isNull()).isTrue();
        assertThat(body.path("status").asText()).isEqualTo("SUCCESS");
    }

    @Test
    void passesStandardInputToJava() {
        ResponseEntity<JsonNode> response = execute(Map.of(
                "language", "java",
                "code", """
                        import java.util.Scanner;

                        public class Main {
                            public static void main(String[] args) {
                                Scanner scanner = new Scanner(System.in);
                                String name = scanner.nextLine();
                                System.out.println("Hello, " + name + "!");
                            }
                        }
                        """,
                "input", "Ada\n"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("output").asText())
                .isEqualTo("Hello, Ada!\n");
    }

    @Test
    void separatesStandardOutputAndStandardError() {
        ResponseEntity<JsonNode> response = execute(Map.of(
                "language", "java",
                "code", """
                        public class Main {
                            public static void main(String[] args) {
                                System.out.println("out");
                                System.err.println("err");
                            }
                        }
                        """,
                "input", ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("output").asText()).isEqualTo("out\n");
        assertThat(body.path("stderr").asText()).isEqualTo("err\n");
        assertThat(body.path("error").isNull()).isTrue();
        assertThat(body.path("status").asText()).isEqualTo("SUCCESS");
    }

    @Test
    void reportsCompilationFailureWithoutAnHttpError() {
        ResponseEntity<JsonNode> response = execute(Map.of(
                "language", "java",
                "code", """
                        public class Main {
                            public static void main(String[] args) {
                                missingMethod();
                            }
                        }
                        """,
                "input", ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
        assertThat(response.getBody().path("error").asText()).contains("missingMethod");
        assertThat(response.getBody().path("status").asText()).isEqualTo("COMPILE_ERROR");
    }

    @Test
    void rubyStillAnswers501() {
        ResponseEntity<JsonNode> response = execute(Map.of(
                "language", "ruby",
                "code", "puts 'hello'",
                "input", ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
        assertThat(response.getBody().path("status").asInt()).isEqualTo(501);
    }

    private ResponseEntity<JsonNode> execute(Map<String, String> body) {
        return restTemplate.postForEntity("/api/execute", body, JsonNode.class);
    }
}
