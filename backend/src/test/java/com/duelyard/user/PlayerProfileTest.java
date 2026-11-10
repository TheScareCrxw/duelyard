package com.duelyard.user;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.duelyard.auth.CognitoUserInfo;
import com.duelyard.auth.CognitoUserInfoClient;
import com.duelyard.game.GameMode;
import com.duelyard.game.GameRepository;
import com.duelyard.match.Match;
import com.duelyard.match.MatchEndReason;
import com.duelyard.match.MatchRepository;
import com.duelyard.match.MatchResult;
import com.duelyard.match.MatchStatus;
import com.duelyard.support.IntegrationTest;
import com.duelyard.support.TestAccounts;

@IntegrationTest
class PlayerProfileTest {

    @Autowired MockMvc mvc;
    @Autowired CognitoUserInfoClient cognito;
    @Autowired UserRepository users;
    @Autowired GameRepository games;
    @Autowired MatchRepository matches;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    private TestAccounts accounts;
    private String alice;
    private String bob;

    @BeforeEach
    void setUp() throws Exception {
        reset(cognito);
        accounts = new TestAccounts("prf");
        alice = "alice_" + accounts.tag();
        bob = "bob_" + accounts.tag();
        provision("alice", alice);
        provision("bob", bob);
    }

    @AfterEach
    void cleanUp() {
        accounts.cleanUp(jdbc);
    }

    private void provision(String who, String username) throws Exception {
        when(cognito.fetch(anyString()))
            .thenReturn(new CognitoUserInfo("opaque", username + "@secret.example", true, false, username));
        mvc.perform(get("/api/users/me").with(accounts.as(who))).andExpect(status().isOk());
    }

    /** A completed ranked darts match alice won, ending {@code minutesAgo} ago. */
    private void aliceBeatsBob(int minutesAgo) {
        tx.executeWithoutResult(status -> {
            User a = users.findByUsernameIgnoreCase(alice).orElseThrow();
            User b = users.findByUsernameIgnoreCase(bob).orElseThrow();
            Instant end = Instant.now().minus(minutesAgo, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MICROS);
            Match m = new Match(games.findBySlug("darts").orElseThrow(), GameMode.RANKED);
            m.addPlayer(a, 0);
            m.addPlayer(b, 1);
            m.start(end.minusSeconds(300));
            m.end(MatchStatus.COMPLETED, MatchEndReason.NORMAL, end);
            m.recordResult(a.getId(), MatchResult.WIN, 501);
            m.recordResult(b.getId(), MatchResult.LOSS, 120);
            m.recordRatingChange(a.getId(), 1000, 1016);
            m.recordRatingChange(b.getId(), 1000, 984);
            matches.save(m);
        });
    }

    @Test
    void publicProfileIsVisibleSignedOutWithoutPrivateFields() throws Exception {
        mvc.perform(get("/api/players/" + alice.toUpperCase()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value(alice))
            .andExpect(jsonPath("$.self").value(false))
            .andExpect(jsonPath("$.restricted").value(false))
            .andExpect(jsonPath("$.ratings", hasSize(4)))
            .andExpect(jsonPath("$.ratings[0].rating").value(1000))
            .andExpect(jsonPath("$.summary.gamesPlayed").value(0))
            .andExpect(jsonPath("$.summary.winRate").value(nullValue()))
            .andExpect(jsonPath("$.summary.highestRating").value(1000))
            .andExpect(jsonPath("$.recentMatches", hasSize(0)))
            .andExpect(content().string(Matchers.not(Matchers.containsString("secret.example"))))
            .andExpect(content().string(Matchers.not(Matchers.containsString(accounts.sub("alice")))));
    }

    @Test
    void summaryAggregatesAcrossGamesAndModes() throws Exception {
        jdbc.update("""
            UPDATE player_game_stats SET ranked_wins = 3, ranked_losses = 1, casual_draws = 1, rating = 1040, peak_rating = 1072
            WHERE user_id = (SELECT id FROM users WHERE username = ?)
              AND game_id = (SELECT id FROM games WHERE slug = 'darts')
            """, alice);
        jdbc.update("""
            UPDATE player_game_stats SET casual_wins = 1, ranked_losses = 2
            WHERE user_id = (SELECT id FROM users WHERE username = ?)
              AND game_id = (SELECT id FROM games WHERE slug = 'eight-ball')
            """, alice);

        mvc.perform(get("/api/players/" + alice))
            .andExpect(jsonPath("$.summary.gamesPlayed").value(8))
            .andExpect(jsonPath("$.summary.wins").value(4))
            .andExpect(jsonPath("$.summary.losses").value(3))
            .andExpect(jsonPath("$.summary.draws").value(1))
            .andExpect(jsonPath("$.summary.winRate").value(closeTo(0.5, 1e-9)))
            .andExpect(jsonPath("$.summary.highestRating").value(1072));
    }

    @Test
    void recentMatchesShowResultRatingChangeAndOpponent() throws Exception {
        aliceBeatsBob(10);

        mvc.perform(get("/api/players/" + alice))
            .andExpect(jsonPath("$.recentMatches", hasSize(1)))
            .andExpect(jsonPath("$.recentMatches[0].gameSlug").value("darts"))
            .andExpect(jsonPath("$.recentMatches[0].mode").value("RANKED"))
            .andExpect(jsonPath("$.recentMatches[0].result").value("WIN"))
            .andExpect(jsonPath("$.recentMatches[0].ratingChange").value(16))
            .andExpect(jsonPath("$.recentMatches[0].opponents[0].username").value(bob))
            .andExpect(jsonPath("$.recentMatches[0].opponents[0].result").value("LOSS"));

        mvc.perform(get("/api/players/" + bob))
            .andExpect(jsonPath("$.recentMatches[0].result").value("LOSS"))
            .andExpect(jsonPath("$.recentMatches[0].ratingChange").value(-16));
    }

    @Test
    void matchHistoryPaginatesNewestFirst() throws Exception {
        aliceBeatsBob(30);
        aliceBeatsBob(20);
        aliceBeatsBob(10);

        mvc.perform(get("/api/players/" + alice + "/matches").param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.hasMore").value(true));
        mvc.perform(get("/api/players/" + alice + "/matches").param("size", "2").param("page", "1"))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.hasMore").value(false));
        mvc.perform(get("/api/players/" + alice + "/matches").param("size", "500"))
            .andExpect(jsonPath("$.size").value(50));
    }

    @Test
    void privateProfileHidesStatsFromOthersButNotFromOwner() throws Exception {
        jdbc.update("UPDATE users SET profile_visibility = 'PRIVATE' WHERE username = ?", alice);

        mvc.perform(get("/api/players/" + alice))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.restricted").value(true))
            .andExpect(jsonPath("$.ratings").value(nullValue()))
            .andExpect(jsonPath("$.summary").value(nullValue()))
            .andExpect(jsonPath("$.recentMatches").value(nullValue()));
        mvc.perform(get("/api/players/" + alice).with(accounts.as("bob")))
            .andExpect(jsonPath("$.restricted").value(true));
        mvc.perform(get("/api/players/" + alice + "/matches"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PROFILE_PRIVATE"));

        mvc.perform(get("/api/players/" + alice).with(accounts.as("alice")))
            .andExpect(jsonPath("$.self").value(true))
            .andExpect(jsonPath("$.restricted").value(false))
            .andExpect(jsonPath("$.ratings", hasSize(4)));
        mvc.perform(get("/api/players/" + alice + "/matches").with(accounts.as("alice")))
            .andExpect(status().isOk());
    }

    @Test
    void unknownPlayerIs404() throws Exception {
        mvc.perform(get("/api/players/nobody_" + accounts.tag())).andExpect(status().isNotFound());
    }
}
