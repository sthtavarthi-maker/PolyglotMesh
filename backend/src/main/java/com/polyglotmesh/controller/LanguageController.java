package com.polyglotmesh.controller;

import com.polyglotmesh.dto.LanguagesResponse;
import com.polyglotmesh.service.ExecutionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only capability endpoint.
 *
 * <p>Lists every product language with its current executability, so clients do
 * not have to discover support by sending code and receiving 501. Availability
 * is checked without creating a GraalVM engine or a child JVM.</p>
 */
@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class LanguageController {

    private final ExecutionService executionService;

    public LanguageController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @GetMapping("/languages")
    public LanguagesResponse languages() {
        return new LanguagesResponse(executionService.languageStatuses());
    }
}
