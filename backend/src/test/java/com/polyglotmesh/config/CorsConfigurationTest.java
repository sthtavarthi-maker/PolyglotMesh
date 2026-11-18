package com.polyglotmesh.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Verifies the CORS policy against the running application rather than by
 * inspecting configuration objects.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorsConfigurationTest {

    private static final String VITE_DEV_ORIGIN = "http://localhost:5173";

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void allowsViteDevelopmentOrigin() {
        ResponseEntity<String> response =
                get("/api/health", VITE_DEV_ORIGIN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowOrigin())
                .isEqualTo(VITE_DEV_ORIGIN);
    }

    @Test
    void allowsPreflightRequestFromViteDevelopmentOrigin() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(VITE_DEV_ORIGIN);
        headers.setAccessControlRequestMethod(HttpMethod.POST);
        headers.setAccessControlRequestHeaders(List.of("content-type"));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/health", HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowOrigin())
                .isEqualTo(VITE_DEV_ORIGIN);
        assertThat(response.getHeaders().getAccessControlAllowMethods())
                .contains(HttpMethod.POST);
    }

    @Test
    void rejectsUnknownOrigin() {
        ResponseEntity<String> response =
                get("/api/health", "http://untrusted.example");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
    }

    private ResponseEntity<String> get(String path, String origin) {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(origin);
        return restTemplate.exchange(
                path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
