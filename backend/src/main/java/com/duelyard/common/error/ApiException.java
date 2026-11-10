package com.duelyard.common.error;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * A client-facing error with a stable machine-readable {@code code} (e.g. {@code USERNAME_TAKEN})
 * that the frontend maps to messages. Rendered as a problem response with {@code code} and any
 * extra {@code properties}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> properties;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, Object> properties) {
        super(message);
        this.status = status;
        this.code = code;
        this.properties = Map.copyOf(properties);
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public Map<String, Object> properties() { return properties; }
}
