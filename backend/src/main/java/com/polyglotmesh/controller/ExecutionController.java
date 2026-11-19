package com.polyglotmesh.controller;

import com.polyglotmesh.dto.ExecutionRequest;
import com.polyglotmesh.dto.ExecutionResponse;
import com.polyglotmesh.service.ExecutionService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unified execution endpoint. HTTP concerns live here; language resolution and
 * routing belong to {@link ExecutionService}.
 */
@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class ExecutionController {

    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping(path = "/execute", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ExecutionResponse> execute(
            @Valid @RequestBody ExecutionRequest request) {
        return ResponseEntity.ok(executionService.execute(request));
    }
}
