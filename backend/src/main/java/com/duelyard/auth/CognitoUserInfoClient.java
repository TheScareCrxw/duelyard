package com.duelyard.auth;

/**
 * Fetches the attributes of the user that owns an access token. Access tokens don't contain the
 * email address, so this is called once, when an application account is first created.
 */
public interface CognitoUserInfoClient {

    CognitoUserInfo fetch(String accessToken);
}
