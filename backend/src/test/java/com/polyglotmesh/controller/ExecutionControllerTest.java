package com.polyglotmesh.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.polyglotmesh.config.ExecutionProperties;
import com.polyglotmesh.exception.GlobalExceptionHandler;
import com.polyglotmesh.service.ExecutionGate;
import com.polyglotmesh.service.ExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the real controller, gate and service with no executor beans in
 * this web slice, so every known language still answers 501.
 */
@WebMvcTest(ExecutionController.class)
@Import({ExecutionService.class, ExecutionGate.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(ExecutionProperties.class)
class ExecutionControllerTest {

    private static final String VALID_BODY = """
            {"language":"javascript","code":"console.log('Hello PolyglotMesh')","input":""}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validRequestReportsExecutionUnavailable() throws Exception {
        mockMvc.perform(post("/api/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.status").value(501))
                .andExpect(jsonPath("$.error").value("Not Implemented"))
                .andExpect(jsonPath("$.message").value(containsString("javascript")))
                .andExpect(jsonPath("$.path").value("/api/execute"))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void blankLanguageIsRejected() throws Exception {
        mockMvc.perform(post("/api/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"language":"  ","code":"print(1)","input":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("language must not be blank")));
    }

    @Test
    void missingLanguageIsRejected() throws Exception {
        mockMvc.perform(post("/api/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"print(1)","input":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("language must not be blank")));
    }

    @Test
    void blankCodeIsRejected() throws Exception {
        mockMvc.perform(post("/api/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"language":"python","code":"   ","input":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("code must not be blank")));
    }

    @Test
    void unknownLanguageIsRejected() throws Exception {
        mockMvc.perform(post("/api/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"language":"cobol","code":"DISPLAY 'HI'","input":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Unknown language 'cobol'")));
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        mockMvc.perform(post("/api/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or missing JSON request body."));
    }

    @Test
    void missingBodyIsRejected() throws Exception {
        mockMvc.perform(post("/api/execute").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or missing JSON request body."));
    }

    @Test
    void unsupportedContentTypeIsRejected() throws Exception {
        mockMvc.perform(post("/api/execute")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void wrongHttpMethodIsRejected() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/execute"))
                .andExpect(status().isMethodNotAllowed());
    }
}
