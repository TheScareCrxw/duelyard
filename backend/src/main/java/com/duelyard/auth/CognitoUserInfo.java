package com.duelyard.auth;

/**
 * User attributes read from Cognito.
 *
 * @param username        the Cognito username: an opaque id for native accounts, or e.g.
 *                        {@code google_1234...} for federated ones. Never shown to players.
 * @param email           the user's email address
 * @param emailVerified   whether Cognito considers the email verified
 * @param federated       true when the account was created through an external identity provider
 * @param desiredUsername the username chosen at sign-up ({@code custom:desired_username}), if any
 */
public record CognitoUserInfo(
        String username, String email, boolean emailVerified, boolean federated, String desiredUsername) {
}
