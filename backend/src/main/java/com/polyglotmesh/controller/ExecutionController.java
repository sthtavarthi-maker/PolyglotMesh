package com.polyglotmesh.controller;

import com.polyglotmesh.dto.ExecutionRequest;
import com.polyglotmesh.dto.ExecutionResponse;
import com.polyglotmesh.service.ExecutionGate;
import com.polyglotmesh.service.ExecutionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unified execution endpoint. HTTP concerns live here; admission control,
 * language resolution and routing belong to {@link ExecutionGate} and
 * {@link ExecutionService}.
 */
@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class ExecutionController {

    private final ExecutionService executionService;
    private final ExecutionGate executionGate;

    public ExecutionController(
            ExecutionService executionService, ExecutionGate executionGate) {
        this.executionService = executionService;
        this.executionGate = executionGate;
    }

    /**
     * Executes one program. The permit is closed after the response is built,
     * so a failed execution cannot leak a concurrency slot.
     */
    @PostMapping(path = "/execute", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ExecutionResponse> execute(
            @Valid @RequestBody ExecutionRequest request, HttpServletRequest httpRequest) {
        try (ExecutionGate.Permit permit = executionGate.acquire(httpRequest.getRemoteAddr())) {
            return ResponseEntity.ok(executionService.execute(request));
        }
    }
}
