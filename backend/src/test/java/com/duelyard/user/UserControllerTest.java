package com.duelyard.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.duelyard.auth.CognitoAliasTakenException;
import com.duelyard.auth.CognitoUnavailableException;
import com.duelyard.auth.CognitoUserInfo;
import com.duelyard.auth.CognitoUserInfoClient;
import com.duelyard.rating.PlayerGameStatsRepository;
import com.duelyard.support.IntegrationTest;
import com.duelyard.support.TestAccounts;

@IntegrationTest
class UserControllerTest {

    @Autowired MockMvc mvc;
    @Autowired CognitoUserInfoClient cognito;
    @Autowired UserRepository users;
    @Autowired PlayerGameStatsRepository stats;
    @Autowired JdbcTemplate jdbc;

    private TestAccounts accounts;
    private String t;

    @BeforeEach
    void setUp() {
        reset(cognito);
        accounts = new TestAccounts("usr");
        t = accounts.tag();
    }

    @AfterEach
    void cleanUp() {
        accounts.cleanUp(jdbc);
    }

    /** Stubs Cognito for a native account that chose {@code desired} at sign-up. */
    private void nativeUser(String desired) {
        when(cognito.fetch(anyString()))
            .thenReturn(new CognitoUserInfo("opaque-" + desired, desired + "@example.com", true, false, desired));
    }

    private ResultActions patchMe(String who, String json) throws Exception {
        return mvc.perform(patch("/api/users/me").with(accounts.as(who))
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // --- provisioning --------------------------------------------------------------------------

    @Test
    void meRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void firstCallProvisionsAccountClaimsAliasAndSeedsRatings() throws Exception {
        String name = "nat_" + t;
        nativeUser(name);

        mvc.perform(get("/api/users/me").with(accounts.as("a")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value(name))
            .andExpect(jsonPath("$.email").value(name + "@example.com"))
            .andExpect(jsonPath("$.usernameChangeAvailableAt").value(nullValue()))
            .andExpect(jsonPath("$.cognitoSub").doesNotExist());

        verify(cognito).setPreferredUsername(anyString(), eq(name));
        User user = users.findByCognitoSub(accounts.sub("a")).orElseThrow();
        assertThat(stats.findByUserId(user.getId())).hasSize(4).allSatisfy(s -> assertThat(s.getRating()).isEqualTo(1000));
    }

    @Test
    void laterCallsReuseTheAccountWithoutCallingCognito() throws Exception {
        nativeUser("rep_" + t);
        mvc.perform(get("/api/users/me").with(accounts.as("b"))).andExpect(status().isOk());
        mvc.perform(get("/api/users/me").with(accounts.as("b"))).andExpect(status().isOk());

        verify(cognito, times(1)).fetch(anyString());
        verify(cognito, times(1)).setPreferredUsername(anyString(), anyString());
    }

    @Test
    void federatedUserGetsUsernameFromEmail() throws Exception {
        when(cognito.fetch(anyString()))
            .thenReturn(new CognitoUserInfo("google_10937465", "g." + t + "@gmail.com", true, true, null));

        mvc.perform(get("/api/users/me").with(accounts.as("c")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("g_" + t));
    }

    @Test
    void takenUsernameGetsNumericSuffix() throws Exception {
        String name = "dup_" + t;
        nativeUser(name);
        mvc.perform(get("/api/users/me").with(accounts.as("d1"))).andExpect(status().isOk());

        mvc.perform(get("/api/users/me").with(accounts.as("d2")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value(matchesPattern(name + "[0-9]{4}")));
    }

    @Test
    void cognitoAliasConflictFallsBackToAnotherName() throws Exception {
        String name = "ali_" + t;
        nativeUser(name);
        doThrow(new CognitoAliasTakenException(name)).when(cognito).setPreferredUsername(anyString(), eq(name));

        mvc.perform(get("/api/users/me").with(accounts.as("al")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value(matchesPattern(name + "[0-9]{4}")));
        assertThat(users.findByUsernameIgnoreCase(name)).isEmpty();
    }

    @Test
    void cognitoOutageReturns503AndCreatesNothing() throws Exception {
        when(cognito.fetch(anyString())).thenThrow(new CognitoUnavailableException("down", null));

        mvc.perform(get("/api/users/me").with(accounts.as("e")))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.detail").value("Account service is temporarily unavailable. Please try again."));
        assertThat(users.findByCognitoSub(accounts.sub("e"))).isEmpty();
    }

    // --- profile settings ----------------------------------------------------------------------

    @Test
    void renameUpdatesUsernameSyncsAliasAndStartsCooldown() throws Exception {
        nativeUser("old_" + t);
        mvc.perform(get("/api/users/me").with(accounts.as("r"))).andExpect(status().isOk());

        patchMe("r", "{\"username\":\"new_" + t + "\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("new_" + t))
            .andExpect(jsonPath("$.usernameChangeAvailableAt").isNotEmpty());
        verify(cognito).setPreferredUsername(anyString(), eq("new_" + t));

        patchMe("r", "{\"username\":\"third_" + t + "\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("USERNAME_CHANGE_TOO_SOON"))
            .andExpect(jsonPath("$.availableAt").isNotEmpty());
    }

    @Test
    void renameAllowedAgainAfterCooldown() throws Exception {
        nativeUser("cd_" + t);
        mvc.perform(get("/api/users/me").with(accounts.as("cd"))).andExpect(status().isOk());
        jdbc.update("UPDATE users SET username_changed_at = now() - interval '31 days' WHERE cognito_sub = ?",
            accounts.sub("cd"));

        patchMe("cd", "{\"username\":\"cd2_" + t + "\"}").andExpect(status().isOk());
    }

    @Test
    void renameRejectsTakenInvalidAndReservedNames() throws Exception {
        nativeUser("own_" + t);
        mvc.perform(get("/api/users/me").with(accounts.as("x1"))).andExpect(status().isOk());
        nativeUser("oth_" + t);
        mvc.perform(get("/api/users/me").with(accounts.as("x2"))).andExpect(status().isOk());

        patchMe("x2", "{\"username\":\"OWN_" + t + "\"}")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        patchMe("x2", "{\"username\":\"no spaces\"}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("USERNAME_INVALID"));
        patchMe("x2", "{\"username\":\"Admin\"}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("USERNAME_RESERVED"));

        assertThat(users.findByCognitoSub(accounts.sub("x2")).orElseThrow().getUsername()).isEqualTo("oth_" + t);
    }

    @Test
    void cognitoAliasConflictRollsBackRename() throws Exception {
        nativeUser("rb_" + t);
        mvc.perform(get("/api/users/me").with(accounts.as("rb"))).andExpect(status().isOk());
        doThrow(new CognitoAliasTakenException("x")).when(cognito).setPreferredUsername(anyString(), eq("rb2_" + t));

        patchMe("rb", "{\"username\":\"rb2_" + t + "\"}")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));

        User user = users.findByCognitoSub(accounts.sub("rb")).orElseThrow();
        assertThat(user.getUsername()).isEqualTo("rb_" + t);
        assertThat(user.getUsernameChangedAt()).isNull();
    }

    @Test
    void updatesAvatarAndVisibility() throws Exception {
        nativeUser("av_" + t);
        mvc.perform(get("/api/users/me").with(accounts.as("av"))).andExpect(status().isOk());

        patchMe("av", "{\"avatarUrl\":\"preset:crown:ocean\",\"profileVisibility\":\"PRIVATE\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.avatarUrl").value("preset:crown:ocean"))
            .andExpect(jsonPath("$.profileVisibility").value("PRIVATE"));
        patchMe("av", "{\"avatarUrl\":\"https://evil.example/x.png\"}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AVATAR_INVALID"));
        patchMe("av", "{\"avatarUrl\":\"\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").value(nullValue()));
        patchMe("av", "{\"profileVisibility\":\"SECRET\"}").andExpect(status().isBadRequest());

        verify(cognito, times(1)).setPreferredUsername(anyString(), anyString());
    }

    @Test
    void patchRequiresAuthentication() throws Exception {
        mvc.perform(patch("/api/users/me").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
    }

    // --- availability --------------------------------------------------------------------------

    @Test
    void usernameAvailabilityIsPublic() throws Exception {
        nativeUser("tkn_" + t);
        mvc.perform(get("/api/users/me").with(accounts.as("f"))).andExpect(status().isOk());

        mvc.perform(get("/api/users/username-availability").param("username", "TKN_" + t))
            .andExpect(jsonPath("$.available").value(false)).andExpect(jsonPath("$.reason").value("TAKEN"));
        mvc.perform(get("/api/users/username-availability").param("username", "free_" + t))
            .andExpect(jsonPath("$.available").value(true));
        mvc.perform(get("/api/users/username-availability").param("username", "bad name"))
            .andExpect(jsonPath("$.reason").value("INVALID_FORMAT"));
        mvc.perform(get("/api/users/username-availability").param("username", "Support"))
            .andExpect(jsonPath("$.reason").value("RESERVED"));
        verify(cognito, never()).fetch("unused");
    }
}
