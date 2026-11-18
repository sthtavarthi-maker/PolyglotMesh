package com.polyglotmesh.dto;

/**
 * Response body of {@code GET /api/health}.
 *
 * @param status      always "UP" while the application is serving requests
 * @param application application name, used by the frontend and deployment checks
 */
public record HealthResponse(String status, String application) {
}
