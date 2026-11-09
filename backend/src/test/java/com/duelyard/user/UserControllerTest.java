package com.duelyard.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.duelyard.auth.CognitoUnavailableException;
import com.duelyard.auth.CognitoUserInfo;
import com.duelyard.auth.CognitoUserInfoClient;
import com.duelyard.rating.PlayerGameStatsRepository;
import com.duelyard.support.IntegrationTest;

/** Provisioning commits in its own transaction, so these tests use unique identities and clean up. */
@IntegrationTest
class UserControllerTest {

    @Autowired MockMvc mvc;
    @Autowired CognitoUserInfoClient cognito;
    @Autowired UserRepository users;
    @Autowired PlayerGameStatsRepository stats;
    @Autowired JdbcTemplate jdbc;

    private String run;

    @BeforeEach
    void setUp() {
        reset(cognito);
        run = UUID.randomUUID().toString().substring(0, 6);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM users WHERE cognito_sub LIKE 'ctl-%'");
    }

    private String sub(String name) {
        return "ctl-" + run + "-" + name;
    }

    private static RequestPostProcessor as(String sub) {
        return jwt().jwt(j -> j.subject(sub).claim("token_use", "access"));
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void firstCallProvisionsAccountWithStartingRatings() throws Exception {
        String name = "nat_" + run;
        when(cognito.fetch(anyString())).thenReturn(new CognitoUserInfo(name, name + "@example.com", true, false));

        mvc.perform(get("/api/users/me").with(as(sub("a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value(name))
            .andExpect(jsonPath("$.email").value(name + "@example.com"))
            .andExpect(jsonPath("$.profileVisibility").value("PUBLIC"))
            .andExpect(jsonPath("$.cognitoSub").doesNotExist());

        User user = users.findByCognitoSub(sub("a")).orElseThrow();
        assertThat(stats.findByUserId(user.getId()))
            .hasSize(4)
            .allSatisfy(s -> assertThat(s.getRating()).isEqualTo(1000));
    }

    @Test
    void laterCallsReuseTheAccountWithoutCallingCognito() throws Exception {
        String name = "rep_" + run;
        when(cognito.fetch(anyString())).thenReturn(new CognitoUserInfo(name, name + "@example.com", true, false));

        mvc.perform(get("/api/users/me").with(as(sub("b")))).andExpect(status().isOk());
        mvc.perform(get("/api/users/me").with(as(sub("b"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value(name));

        verify(cognito, times(1)).fetch(anyString());
        assertThat(users.findAll().stream().filter(u -> u.getCognitoSub().equals(sub("b")))).hasSize(1);
    }

    @Test
    void federatedUserGetsUsernameFromEmail() throws Exception {
        when(cognito.fetch(anyString()))
            .thenReturn(new CognitoUserInfo("google_10937465", "g." + run + "@gmail.com", true, true));

        mvc.perform(get("/api/users/me").with(as(sub("c"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("g_" + run));
    }

    @Test
    void takenUsernameGetsNumericSuffix() throws Exception {
        String name = "dup_" + run;
        when(cognito.fetch(anyString())).thenReturn(new CognitoUserInfo(name, "x@example.com", true, false));
        mvc.perform(get("/api/users/me").with(as(sub("d1")))).andExpect(status().isOk());

        mvc.perform(get("/api/users/me").with(as(sub("d2"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value(org.hamcrest.Matchers.matchesPattern(name + "[0-9]{4}")));
    }

    @Test
    void cognitoOutageReturns503AndCreatesNothing() throws Exception {
        when(cognito.fetch(anyString())).thenThrow(new CognitoUnavailableException("down", null));

        mvc.perform(get("/api/users/me").with(as(sub("e"))))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.detail").value("Account service is temporarily unavailable. Please try again."));

        assertThat(users.findByCognitoSub(sub("e"))).isEmpty();
    }

    @Test
    void usernameAvailabilityIsPublic() throws Exception {
        when(cognito.fetch(anyString())).thenReturn(new CognitoUserInfo("tkn_" + run, "t@example.com", true, false));
        mvc.perform(get("/api/users/me").with(as(sub("f")))).andExpect(status().isOk());

        mvc.perform(get("/api/users/username-availability").param("username", "TKN_" + run))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false))
            .andExpect(jsonPath("$.reason").value("TAKEN"));
        mvc.perform(get("/api/users/username-availability").param("username", "free_" + run))
            .andExpect(jsonPath("$.available").value(true));
        mvc.perform(get("/api/users/username-availability").param("username", "bad name"))
            .andExpect(jsonPath("$.reason").value("INVALID_FORMAT"));
        verify(cognito, never()).fetch("unused");
    }
}
