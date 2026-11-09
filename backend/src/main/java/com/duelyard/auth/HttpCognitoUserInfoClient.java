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
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Calls Cognito's {@code GetUser} and {@code UpdateUserAttributes} APIs with the user's own access
 * token.
 *
 * <p>Cognito speaks {@code application/x-amz-json-1.1}, which Spring's JSON converters don't
 * recognise, so bodies are (de)serialized explicitly and exchanged as strings.
 */
@Component
class HttpCognitoUserInfoClient implements CognitoUserInfoClient {

    private static final MediaType AMZ_JSON = MediaType.parseMediaType("application/x-amz-json-1.1");
    private static final String TARGET_PREFIX = "AWSCognitoIdentityProviderService.";

    private final RestClient client;
    private final JsonMapper json;

    // Builds its own RestClient: Spring Boot 4 only provides a RestClient.Builder bean with the
    // separate restclient module, and this client needs nothing from it.
    @Autowired
    HttpCognitoUserInfoClient(CognitoProperties cognito, JsonMapper json) {
        this(RestClient.builder().requestFactory(timeouts()).baseUrl(cognito.apiEndpoint()).build(), json);
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
        String body = call("GetUser", Map.of("AccessToken", accessToken));
        GetUserResponse response;
        try {
            response = body == null ? null : json.readValue(body, GetUserResponse.class);
        } catch (JacksonException e) {
            throw new CognitoUnavailableException("Unreadable GetUser response", e);
        }
        if (response == null) {
            throw new CognitoUnavailableException("Cognito GetUser returned no body", null);
        }

        String email = null;
        String desiredUsername = null;
        boolean emailVerified = false;
        boolean federated = false;
        for (Attribute attribute : response.UserAttributes() == null ? List.<Attribute>of() : response.UserAttributes()) {
            switch (attribute.Name()) {
                case "email" -> email = attribute.Value();
                case "email_verified" -> emailVerified = Boolean.parseBoolean(attribute.Value());
                case "identities" -> federated = attribute.Value() != null && !attribute.Value().isBlank();
                case "custom:desired_username" -> desiredUsername = attribute.Value();
                default -> { }
            }
        }
        return new CognitoUserInfo(response.Username(), email, emailVerified, federated, desiredUsername);
    }

    @Override
    public void setPreferredUsername(String accessToken, String username) {
        try {
            call("UpdateUserAttributes", Map.of(
                "AccessToken", accessToken,
                "UserAttributes", List.of(Map.of("Name", "preferred_username", "Value", username))));
        } catch (CognitoErrorResponse e) {
            if ("AliasExistsException".equals(e.type)) {
                throw new CognitoAliasTakenException(username);
            }
            throw new CognitoUnavailableException("Cognito UpdateUserAttributes failed: " + e.type, e);
        }
    }

    private String call(String operation, Map<String, ?> request) {
        try {
            return client.post()
                .header("X-Amz-Target", TARGET_PREFIX + operation)
                .contentType(AMZ_JSON)
                .accept(AMZ_JSON, MediaType.APPLICATION_JSON)
                .body(json.writeValueAsString(request))
                .retrieve()
                .body(String.class);
        } catch (RestClientResponseException e) {
            String type = errorType(e.getResponseBodyAsString());
            if (e.getStatusCode().is4xxClientError() && type != null) {
                throw new CognitoErrorResponse(operation, type, e);
            }
            throw new CognitoUnavailableException("Cognito " + operation + " failed: " + e.getStatusCode(), e);
        } catch (RestClientException | JacksonException e) {
            throw new CognitoUnavailableException("Cognito " + operation + " failed: " + e.getMessage(), e);
        }
    }

    /** Cognito error bodies look like {"__type":"...#AliasExistsException","message":"..."}. */
    private String errorType(String body) {
        try {
            ErrorBody error = json.readValue(body, ErrorBody.class);
            return error.__type() == null ? null : error.__type().substring(error.__type().lastIndexOf('#') + 1);
        } catch (JacksonException e) {
            return null;
        }
    }

    /** A 4xx error Cognito described with an error type. Surfaced as a domain exception or 503. */
    private static final class CognitoErrorResponse extends CognitoUnavailableException {
        final String type;

        CognitoErrorResponse(String operation, String type, Throwable cause) {
            super("Cognito " + operation + " rejected: " + type, cause);
            this.type = type;
        }
    }

    // Cognito uses PascalCase JSON field names.
    record GetUserResponse(String Username, List<Attribute> UserAttributes) {
    }

    record Attribute(String Name, String Value) {
    }

    record ErrorBody(String __type, String message) {
    }
}
