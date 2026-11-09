package com.duelyard.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origins permitted to call the API from a browser. Bound from {@code app.cors.*}.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
