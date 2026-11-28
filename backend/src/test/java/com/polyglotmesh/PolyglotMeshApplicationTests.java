package com.polyglotmesh;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.polyglotmesh.model.Language;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Starts the real application on a random port and checks the public bootstrap
 * contracts: the context boots, health/info answer, and language capabilities
 * are discoverable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PolyglotMeshApplicationTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void contextLoads() {
        // Fails if the application context cannot start.
    }

    @Test
    void healthEndpointReturnsUpAndApplicationName() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"status\":\"UP\"")
                .contains("\"application\":\"PolyglotMesh\"");
    }

    @Test
    void actuatorHealthIsUp() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void infoEndpointExposesApplicationAndBuildMetadata() {
        ResponseEntity<JsonNode> response =
                restTemplate.getForEntity("/actuator/info", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("app").path("name").asText())
                .isEqualTo("PolyglotMesh");
        assertThat(response.getBody().path("build").path("version").asText())
                .isEqualTo("0.0.1-SNAPSHOT");
        assertThat(response.getBody().path("build").path("artifact").asText())
                .isEqualTo("polyglotmesh-backend");
    }

    @Test
    void metricsEndpointIsExposed() {
        // Any request creates the built-in HTTP server meter.
        restTemplate.getForEntity("/api/health", String.class);

        ResponseEntity<JsonNode> response =
                restTemplate.getForEntity("/actuator/metrics", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("names").toString())
                .contains("http.server.requests");
    }

    @Test
    void languagesEndpointReportsEveryLanguageWithAvailability() {
        ResponseEntity<JsonNode> response =
                restTemplate.getForEntity("/api/languages", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode languages = response.getBody().path("languages");
        assertThat(languages.size()).isEqualTo(Language.values().length);

        Map<String, Boolean> availability = new HashMap<>();
        languages.forEach(node ->
                availability.put(node.path("id").asText(), node.path("available").asBoolean()));

        assertThat(availability)
                .hasSize(Language.values().length)
                .containsEntry("java", true)
                .containsEntry("javascript", true)
                .containsEntry("python", true)
                .containsEntry("ruby", false)
                .containsEntry("c", false);

        assertThat(languages.get(0).path("id").asText()).isEqualTo("java");
        assertThat(languages.get(0).path("displayName").asText()).isEqualTo("Java");
    }
}
