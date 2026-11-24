package com.polyglotmesh.exception;

import com.polyglotmesh.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates exceptions into one predictable JSON error shape.
 *
 * <p>Unexpected failures are logged server-side but reported as a generic 500:
 * exception messages and stack traces are not sent to clients.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UnsupportedLanguageException.class)
    public ResponseEntity<ApiError> handleUnsupportedLanguage(
            UnsupportedLanguageException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(ExecutionUnavailableException.class)
    public ResponseEntity<ApiError> handleExecutionUnavailable(
            ExecutionUnavailableException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_IMPLEMENTED, exception.getMessage(), request);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiError> handleTooManyRequests(
            TooManyRequestsException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()))
                .body(body(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage(), request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + " " + fieldError.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        String message = detail.isBlank() ? "Request validation failed." : detail;
        return error(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Malformed or missing JSON request body.", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return error(HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method not supported for this endpoint.", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported content type. Use application/json.", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            NoResourceFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "Resource not found.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(),
                exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error.", request);
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(body(status, message, request));
    }

    private ApiError body(HttpStatus status, String message, HttpServletRequest request) {
        return new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI());
    }
}
