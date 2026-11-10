package com.duelyard.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * JWT decoder for Cognito access tokens. Signing keys are fetched lazily from the pool's JWKS
 * endpoint (and cached), so startup never depends on reaching AWS.
 */
@Configuration
@EnableConfigurationProperties(CognitoProperties.class)
public class CognitoJwtConfig {

    @Bean
    JwtDecoder jwtDecoder(CognitoProperties cognito) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(cognito.jwksUri()).build();
        decoder.setJwtValidator(validator(cognito));
        return decoder;
    }

    /** Expiry/not-before (with default clock skew), issuer, then Cognito access-token checks. */
    public static OAuth2TokenValidator<Jwt> validator(CognitoProperties cognito) {
        return new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(cognito.issuer()),
            new CognitoAccessTokenValidator(cognito.clientIds()));
    }
}
