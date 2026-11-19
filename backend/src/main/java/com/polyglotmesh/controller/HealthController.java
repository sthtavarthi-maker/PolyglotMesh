package com.polyglotmesh.controller;

import com.polyglotmesh.dto.HealthResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight liveness endpoint used by the frontend and by manual smoke tests.
 *
 * <p>Actuator's own {@code /actuator/health} stays available for infrastructure
 * probes; this endpoint is the stable, application-specific contract.</p>
 */
@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class HealthController {

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", "PolyglotMesh");
    }
}
