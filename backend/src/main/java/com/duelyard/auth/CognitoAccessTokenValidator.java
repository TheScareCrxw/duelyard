package com.duelyard.auth;

import java.util.Collection;
import java.util.Set;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Cognito-specific checks on top of signature, issuer and expiry validation.
 *
 * <p>Cognito access tokens carry no {@code aud} claim. Instead, they identify the app client in
 * {@code client_id} and mark themselves with {@code token_use=access}. Rejecting anything else
 * stops ID tokens, or tokens minted for a different app client in the same pool, from being
 * accepted as API credentials.
 */
public class CognitoAccessTokenValidator implements OAuth2TokenValidator<Jwt> {

    private final Set<String> allowedClientIds;

    public CognitoAccessTokenValidator(Collection<String> allowedClientIds) {
        if (allowedClientIds.isEmpty()) {
            throw new IllegalArgumentException("At least one Cognito app client id must be configured");
        }
        this.allowedClientIds = Set.copyOf(allowedClientIds);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (!"access".equals(jwt.getClaimAsString("token_use"))) {
            return failure("Token is not a Cognito access token");
        }
        String clientId = jwt.getClaimAsString("client_id");
        if (clientId == null || !allowedClientIds.contains(clientId)) {
            return failure("Token was issued to an unrecognized app client");
        }
        if (jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            return failure("Token has no subject");
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null));
    }
}
