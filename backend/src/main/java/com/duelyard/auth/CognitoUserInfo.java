package com.duelyard.auth;

/**
 * User attributes read from Cognito.
 *
 * @param username      the Cognito username (for federated users, e.g. {@code google_1234...})
 * @param email         the user's email address
 * @param emailVerified whether Cognito considers the email verified
 * @param federated     true when the account was created through an external identity provider
 */
public record CognitoUserInfo(String username, String email, boolean emailVerified, boolean federated) {
}
