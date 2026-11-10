package com.duelyard.auth;

/**
 * Calls Cognito on behalf of the user who owns an access token. Both operations are authorized
 * by the token itself (scope {@code aws.cognito.signin.user.admin}); no AWS credentials needed.
 */
public interface CognitoUserInfoClient {

    /** The user's attributes. Access tokens don't carry the email, so this is read once, at sign-up. */
    CognitoUserInfo fetch(String accessToken);

    /**
     * Sets the {@code preferred_username} alias, which is what players type to sign in.
     *
     * @throws CognitoAliasTakenException if another Cognito user already holds that alias
     */
    void setPreferredUsername(String accessToken, String username);
}
