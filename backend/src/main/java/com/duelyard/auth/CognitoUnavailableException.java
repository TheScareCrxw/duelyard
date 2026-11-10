package com.duelyard.auth;

/** Cognito could not be reached or returned an unusable response. Mapped to HTTP 503. */
public class CognitoUnavailableException extends RuntimeException {

    public CognitoUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
