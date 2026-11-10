package com.duelyard.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

class HttpCognitoUserInfoClientTest {

    private static final CognitoProperties COGNITO = new CognitoProperties("eu-west-2", "eu-west-2_Pool", List.of("c"));
    private static final String ENDPOINT = "https://cognito-idp.eu-west-2.amazonaws.com/";
    private static final MediaType AMZ = MediaType.parseMediaType("application/x-amz-json-1.1");

    private MockRestServiceServer server;
    private HttpCognitoUserInfoClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpCognitoUserInfoClient(builder.baseUrl(COGNITO.apiEndpoint()).build(), JsonMapper.builder().build());
    }

    @Test
    void parsesNativeUserWithDesiredUsername() {
        server.expect(requestTo(ENDPOINT))
            .andExpect(header("X-Amz-Target", "AWSCognitoIdentityProviderService.GetUser"))
            .andExpect(content().json("{\"AccessToken\":\"tok\"}"))
            .andRespond(withSuccess("""
                {"Username":"6f1c-opaque","UserAttributes":[
                  {"Name":"sub","Value":"abc"},
                  {"Name":"email","Value":"alice@example.com"},
                  {"Name":"email_verified","Value":"true"},
                  {"Name":"custom:desired_username","Value":"Alice_1"}]}
                """, AMZ));

        assertThat(client.fetch("tok"))
            .isEqualTo(new CognitoUserInfo("6f1c-opaque", "alice@example.com", true, false, "Alice_1"));
        server.verify();
    }

    @Test
    void detectsFederatedUser() {
        server.expect(requestTo(ENDPOINT))
            .andRespond(withSuccess("""
                {"Username":"google_1093","UserAttributes":[
                  {"Name":"email","Value":"bob@gmail.com"},
                  {"Name":"identities","Value":"[{}]"}]}
                """, MediaType.APPLICATION_JSON));

        CognitoUserInfo info = client.fetch("tok");
        assertThat(info.federated()).isTrue();
        assertThat(info.desiredUsername()).isNull();
    }

    @Test
    void wrapsGetUserErrors() {
        server.expect(requestTo(ENDPOINT))
            .andRespond(withBadRequest().body("{\"__type\":\"NotAuthorizedException\"}"));

        assertThatThrownBy(() -> client.fetch("tok")).isInstanceOf(CognitoUnavailableException.class);
    }

    @Test
    void setsPreferredUsernameAlias() {
        server.expect(requestTo(ENDPOINT))
            .andExpect(header("X-Amz-Target", "AWSCognitoIdentityProviderService.UpdateUserAttributes"))
            .andExpect(content().json("""
                {"AccessToken":"tok","UserAttributes":[{"Name":"preferred_username","Value":"ace"}]}
                """))
            .andRespond(withSuccess("{}", AMZ));

        assertThatCode(() -> client.setPreferredUsername("tok", "ace")).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void aliasConflictBecomesAliasTakenException() {
        server.expect(requestTo(ENDPOINT))
            .andRespond(withBadRequest().body(
                "{\"__type\":\"com.amazonaws.cognito#AliasExistsException\",\"message\":\"exists\"}"));

        assertThatThrownBy(() -> client.setPreferredUsername("tok", "ace")).isInstanceOf(CognitoAliasTakenException.class);
    }

    @Test
    void otherAliasFailuresAreUnavailable() {
        server.expect(requestTo(ENDPOINT)).andRespond(withServerError());

        assertThatThrownBy(() -> client.setPreferredUsername("tok", "ace"))
            .isInstanceOf(CognitoUnavailableException.class)
            .isNotInstanceOf(CognitoAliasTakenException.class);
    }
}
