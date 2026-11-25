package com.polyglotmesh.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polyglotmesh.dto.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects execution request bodies that exceed the configured byte limit.
 *
 * <p>Field-level validation runs after Jackson has parsed the whole body, so an
 * attacker could otherwise send an arbitrarily large payload and make the
 * backend buffer it before receiving a 400. This filter reads at most
 * {@code maxRequestBytes + 1} bytes, returns 413 as soon as the limit is
 * crossed, and forwards an in-memory copy to Spring MVC. No other endpoint is
 * buffered.</p>
 *
 * <p>The limit is intentionally larger than the source and stdin field limits:
 * JSON escaping can expand a valid 100,000-character program. It is a guard
 * against grossly oversized requests, not a replacement for field validation.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    static final String EXECUTION_PATH = "/api/execute";

    private final ObjectMapper objectMapper;
    private final int maxRequestBytes;

    public RequestSizeLimitFilter(
            ObjectMapper objectMapper,
            @Value("${polyglotmesh.security.max-request-bytes:1048576}") int maxRequestBytes) {
        this.objectMapper = objectMapper;
        this.maxRequestBytes = Math.max(1, maxRequestBytes);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return !EXECUTION_PATH.equals(executionPath(request));
    }

    /**
     * Resolves the application-relative path, ignoring a context path, matrix
     * parameters and a trailing slash. Without this, a request such as
     * {@code /api/execute;jsessionid=x} or {@code /api/execute/} could reach the
     * controller while skipping the size guard.
     */
    private static String executionPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = contextPath.isEmpty() ? uri : uri.substring(contextPath.length());
        int semicolon = path.indexOf(';');
        if (semicolon >= 0) {
            path = path.substring(0, semicolon);
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "no-store");

        byte[] body;
        try (ServletInputStream input = request.getInputStream()) {
            int limit = maxRequestBytes == Integer.MAX_VALUE
                    ? Integer.MAX_VALUE
                    : maxRequestBytes + 1;
            body = input.readNBytes(limit);
        }

        if (body.length > maxRequestBytes) {
            writePayloadTooLarge(response, request);
            return;
        }

        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private void writePayloadTooLarge(
            HttpServletResponse response, HttpServletRequest request) throws IOException {
        HttpStatus status = HttpStatus.PAYLOAD_TOO_LARGE;
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiError error = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                "Request body exceeds the " + maxRequestBytes + "-byte limit.",
                request.getRequestURI());
        try (OutputStream output = response.getOutputStream()) {
            objectMapper.writeValue(output, error);
        }
    }

    /** Request whose body has already been read into a bounded byte array. */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new ByteArrayServletInputStream(body);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null
                    ? StandardCharsets.UTF_8
                    : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }

    /** Minimal synchronous stream over the buffered body. */
    private static final class ByteArrayServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream input;

        private ByteArrayServletInputStream(byte[] body) {
            this.input = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return input.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            return input.read(buffer, offset, length);
        }

        @Override
        public int available() {
            return input.available();
        }

        @Override
        public boolean isFinished() {
            return input.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            throw new UnsupportedOperationException("Async request bodies are not supported.");
        }
    }
}
