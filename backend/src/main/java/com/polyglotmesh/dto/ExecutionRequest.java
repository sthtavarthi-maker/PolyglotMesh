package com.polyglotmesh.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body of {@code POST /api/execute}.
 *
 * <p>Limits are deliberately enforced at the API boundary: source code and
 * standard input are attacker-controlled, so oversized payloads are rejected
 * before they reach any executor.</p>
 *
 * @param language known language id, e.g. {@code javascript}
 * @param code     program source; must not be blank
 * @param input    standard input for the program; null is treated as empty
 */
public record ExecutionRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = MAX_LANGUAGE_LENGTH, message = "must be at most " + MAX_LANGUAGE_LENGTH + " characters")
        String language,

        @NotBlank(message = "must not be blank")
        @Size(max = MAX_CODE_LENGTH, message = "must be at most " + MAX_CODE_LENGTH + " characters")
        String code,

        @Size(max = MAX_INPUT_LENGTH, message = "must be at most " + MAX_INPUT_LENGTH + " characters")
        String input) {

    public static final int MAX_LANGUAGE_LENGTH = 32;
    public static final int MAX_CODE_LENGTH = 100_000;
    public static final int MAX_INPUT_LENGTH = 10_000;

    /** Standard input, never null for executors. */
    public String inputOrEmpty() {
        return input == null ? "" : input;
    }
}
