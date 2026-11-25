package com.polyglotmesh.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExecutionRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void acceptsValidRequest() {
        ExecutionRequest request =
                new ExecutionRequest("javascript", "console.log('Hello PolyglotMesh')", "");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void acceptsMissingInput() {
        ExecutionRequest request = new ExecutionRequest("python", "print(1)", null);

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.inputOrEmpty()).isEmpty();
    }

    @Test
    void rejectsBlankLanguage() {
        ExecutionRequest request = new ExecutionRequest("   ", "print(1)", "");

        assertThat(invalidFields(request)).containsExactly("language");
        assertThat(request.inputOrEmpty()).isEmpty();
    }

    @Test
    void rejectsNullLanguage() {
        ExecutionRequest request = new ExecutionRequest(null, "print(1)", "");

        assertThat(invalidFields(request)).containsExactly("language");
    }

    @Test
    void rejectsBlankCode() {
        ExecutionRequest request = new ExecutionRequest("python", "  \n ", "");

        assertThat(invalidFields(request)).containsExactly("code");
    }

    @Test
    void rejectsNullCode() {
        ExecutionRequest request = new ExecutionRequest("python", null, "");

        assertThat(invalidFields(request)).containsExactly("code");
    }

    @Test
    void acceptsCodeAtTheLimit() {
        String atLimit = "x".repeat(ExecutionRequest.MAX_CODE_LENGTH);
        ExecutionRequest request = new ExecutionRequest("python", atLimit, "");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void acceptsInputAtTheLimit() {
        String atLimit = "x".repeat(ExecutionRequest.MAX_INPUT_LENGTH);
        ExecutionRequest request = new ExecutionRequest("python", "print(1)", atLimit);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsOversizedCode() {
        String oversized = "x".repeat(ExecutionRequest.MAX_CODE_LENGTH + 1);
        ExecutionRequest request = new ExecutionRequest("python", oversized, "");

        assertThat(invalidFields(request)).containsExactly("code");
    }

    @Test
    void rejectsOversizedInput() {
        String oversized = "x".repeat(ExecutionRequest.MAX_INPUT_LENGTH + 1);
        ExecutionRequest request = new ExecutionRequest("python", "print(1)", oversized);

        assertThat(invalidFields(request)).containsExactly("input");
    }

    @Test
    void rejectsOversizedLanguage() {
        String oversized = "x".repeat(ExecutionRequest.MAX_LANGUAGE_LENGTH + 1);
        ExecutionRequest request = new ExecutionRequest(oversized, "print(1)", "");

        assertThat(invalidFields(request)).containsExactly("language");
    }

    private Set<String> invalidFields(ExecutionRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }
}
