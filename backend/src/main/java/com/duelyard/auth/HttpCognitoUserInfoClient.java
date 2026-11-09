package com.duelyard.auth;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Calls Cognito's {@code GetUser} API. That operation is authorized by the user's own access token
 * (it needs the {@code aws.cognito.signin.user.admin} scope), so no AWS credentials are required.
 *
 * <p>Cognito speaks {@code application/x-amz-json-1.1}, which Spring's JSON converters don't
 * recognise, so bodies are (de)serialized explicitly and exchanged as strings.
 */
@Component
class HttpCognitoUserInfoClient implements CognitoUserInfoClient {

    private static final MediaType AMZ_JSON = MediaType.parseMediaType("application/x-amz-json-1.1");

    private final RestClient client;
    private final JsonMapper json;

    @Autowired
    HttpCognitoUserInfoClient(CognitoProperties cognito, RestClient.Builder builder, JsonMapper json) {
        this(builder.requestFactory(timeouts()).baseUrl(cognito.apiEndpoint()).build(), json);
    }

    /** For tests: uses the given client as-is (e.g. one bound to MockRestServiceServer). */
    HttpCognitoUserInfoClient(RestClient client, JsonMapper json) {
        this.client = client;
        this.json = json;
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return requestFactory;
    }

    @Override
    public CognitoUserInfo fetch(String accessToken) {
        GetUserResponse response;
        try {
            String body = client.post()
                .header("X-Amz-Target", "AWSCognitoIdentityProviderService.GetUser")
                .contentType(AMZ_JSON)
                .accept(AMZ_JSON, MediaType.APPLICATION_JSON)
                .body(json.writeValueAsString(Map.of("AccessToken", accessToken)))
                .retrieve()
                .body(String.class);
            response = body == null ? null : json.readValue(body, GetUserResponse.class);
        } catch (RestClientException | JacksonException e) {
            throw new CognitoUnavailableException("Cognito GetUser failed: " + e.getMessage(), e);
        }
        if (response == null) {
            throw new CognitoUnavailableException("Cognito GetUser returned no body", null);
        }

        String email = null;
        boolean emailVerified = false;
        boolean federated = false;
        for (Attribute attribute : response.UserAttributes() == null ? List.<Attribute>of() : response.UserAttributes()) {
            switch (attribute.Name()) {
                case "email" -> email = attribute.Value();
                case "email_verified" -> emailVerified = Boolean.parseBoolean(attribute.Value());
                case "identities" -> federated = attribute.Value() != null && !attribute.Value().isBlank();
                default -> { }
            }
        }
        return new CognitoUserInfo(response.Username(), email, emailVerified, federated);
    }

    // Cognito uses PascalCase JSON field names.
    record GetUserResponse(String Username, List<Attribute> UserAttributes) {
    }

    record Attribute(String Name, String Value) {
    }
}
