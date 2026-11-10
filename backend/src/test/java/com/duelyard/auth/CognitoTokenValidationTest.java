package com.duelyard.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

/** Exercises the exact validator chain the production JwtDecoder uses. */
class CognitoTokenValidationTest {

    private static final CognitoProperties COGNITO =
        new CognitoProperties("us-east-1", "us-east-1_TestPool", List.of("web-client"));
    private static final OAuth2TokenValidator<Jwt> VALIDATOR = CognitoJwtConfig.validator(COGNITO);

    private static Jwt token(Consumer<Jwt.Builder> customizer) {
        Jwt.Builder builder = Jwt.withTokenValue("t")
            .header("alg", "RS256")
            .issuer(COGNITO.issuer())
            .subject("sub-123")
            .issuedAt(Instant.now().minusSeconds(60))
            .expiresAt(Instant.now().plusSeconds(3600))
            .claim("token_use", "access")
            .claim("client_id", "web-client");
        customizer.accept(builder);
        return builder.build();
    }

    private static boolean valid(Jwt jwt) {
        return !VALIDATOR.validate(jwt).hasErrors();
    }

    @Test
    void acceptsAccessTokenForConfiguredClient() {
        assertThat(valid(token(b -> {}))).isTrue();
    }

    @Test
    void rejectsIdTokens() {
        assertThat(valid(token(b -> b.claim("token_use", "id")))).isFalse();
    }

    @Test
    void rejectsTokensForOtherAppClients() {
        assertThat(valid(token(b -> b.claim("client_id", "some-other-client")))).isFalse();
    }

    @Test
    void rejectsTokensFromOtherIssuers() {
        assertThat(valid(token(b -> b.issuer("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_Evil")))).isFalse();
    }

    @Test
    void rejectsExpiredTokens() {
        assertThat(valid(token(b -> b.issuedAt(Instant.now().minusSeconds(7200)).expiresAt(Instant.now().minusSeconds(3600)))))
            .isFalse();
    }

    @Test
    void issuerAndJwksUrisAreDerivedFromPool() {
        assertThat(COGNITO.issuer()).isEqualTo("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_TestPool");
        assertThat(COGNITO.jwksUri()).endsWith("/us-east-1_TestPool/.well-known/jwks.json");
    }
}
