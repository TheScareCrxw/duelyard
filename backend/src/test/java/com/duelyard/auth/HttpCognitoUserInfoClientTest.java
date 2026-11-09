package com.duelyard.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
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

    private MockRestServiceServer server;
    private HttpCognitoUserInfoClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpCognitoUserInfoClient(builder.baseUrl(COGNITO.apiEndpoint()).build(), JsonMapper.builder().build());
    }

    @Test
    void parsesNativeUser() {
        server.expect(requestTo("https://cognito-idp.eu-west-2.amazonaws.com/"))
            .andExpect(header("X-Amz-Target", "AWSCognitoIdentityProviderService.GetUser"))
            .andExpect(content().json("{\"AccessToken\":\"tok\"}"))
            .andRespond(withSuccess("""
                {"Username":"alice","UserAttributes":[
                  {"Name":"sub","Value":"abc"},
                  {"Name":"email","Value":"alice@example.com"},
                  {"Name":"email_verified","Value":"true"}]}
                """, MediaType.parseMediaType("application/x-amz-json-1.1")));

        CognitoUserInfo info = client.fetch("tok");

        assertThat(info).isEqualTo(new CognitoUserInfo("alice", "alice@example.com", true, false));
        server.verify();
    }

    @Test
    void detectsFederatedUser() {
        server.expect(requestTo("https://cognito-idp.eu-west-2.amazonaws.com/"))
            .andRespond(withSuccess("""
                {"Username":"google_1093","UserAttributes":[
                  {"Name":"email","Value":"bob@gmail.com"},
                  {"Name":"identities","Value":"[{\\"providerName\\":\\"Google\\"}]"}]}
                """, MediaType.APPLICATION_JSON));

        assertThat(client.fetch("tok").federated()).isTrue();
    }

    @Test
    void wrapsCognitoErrors() {
        server.expect(requestTo("https://cognito-idp.eu-west-2.amazonaws.com/"))
            .andRespond(withBadRequest().body("{\"__type\":\"NotAuthorizedException\"}"));

        assertThatThrownBy(() -> client.fetch("tok")).isInstanceOf(CognitoUnavailableException.class);
    }
}
