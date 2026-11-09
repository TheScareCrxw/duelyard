package com.duelyard.game;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.duelyard.support.IntegrationTest;

@IntegrationTest
class GameControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void listsAllFourGamesPublicly() throws Exception {
        mvc.perform(get("/api/games"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(4)))
            .andExpect(jsonPath("$[?(@.slug == 'darts')].displayName").value("Darts"))
            .andExpect(jsonPath("$[?(@.slug == 'eight-ball')].modes[0]").value("RANKED"));
    }

    @Test
    void getsSingleGameBySlug() throws Exception {
        mvc.perform(get("/api/games/mini-golf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName").value("Mini Golf"))
            .andExpect(jsonPath("$.minPlayers").value(2));
    }

    @Test
    void unknownGameReturnsProblemDetail404() throws Exception {
        mvc.perform(get("/api/games/chess"))
            .andExpect(status().isNotFound())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/problem+json"))
            .andExpect(jsonPath("$.detail").value("Unknown game: chess"));
    }

    @Test
    void nonPublicEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/games")).andExpect(status().isUnauthorized());
    }

    @Test
    void corsAllowsConfiguredFrontendOrigin() throws Exception {
        mvc.perform(options("/api/games")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    @Test
    void corsRejectsUnknownOrigin() throws Exception {
        mvc.perform(options("/api/games")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
            .andExpect(status().isForbidden());
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }
}
