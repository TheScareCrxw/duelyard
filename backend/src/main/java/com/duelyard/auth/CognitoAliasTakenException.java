package com.duelyard.auth;

/** Cognito refused a sign-in alias because another user already has it. */
public class CognitoAliasTakenException extends RuntimeException {

    public CognitoAliasTakenException(String alias) {
        super("Cognito alias already in use: " + alias);
    }
}
