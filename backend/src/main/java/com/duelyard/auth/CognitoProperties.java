package com.duelyard.auth;

import java.util.List;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Amazon Cognito user pool settings, bound from {@code app.cognito.*}.
 *
 * @param region     AWS region of the user pool, e.g. {@code us-east-1}
 * @param userPoolId user pool id, e.g. {@code us-east-1_AbCdEf123}
 * @param clientIds  app client ids whose access tokens this API accepts
 */
@ConfigurationProperties(prefix = "app.cognito")
public record CognitoProperties(String region, String userPoolId, List<String> clientIds) {

    public CognitoProperties {
        Objects.requireNonNull(region, "app.cognito.region");
        Objects.requireNonNull(userPoolId, "app.cognito.user-pool-id");
        clientIds = clientIds == null ? List.of() : List.copyOf(clientIds);
    }

    /** Token issuer: {@code https://cognito-idp.<region>.amazonaws.com/<userPoolId>}. */
    public String issuer() {
        return "https://cognito-idp.%s.amazonaws.com/%s".formatted(region, userPoolId);
    }

    public String jwksUri() {
        return issuer() + "/.well-known/jwks.json";
    }

    /** Regional Cognito Identity Provider API endpoint. */
    public String apiEndpoint() {
        return "https://cognito-idp.%s.amazonaws.com/".formatted(region);
    }
}
