package com.duelyard.leaderboard;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.duelyard.game.GameRepository;
import com.duelyard.rating.PlayerGameStats;
import com.duelyard.rating.PlayerGameStatsRepository;
import com.duelyard.support.IntegrationTest;
import com.duelyard.support.TestAccounts;
import com.duelyard.user.User;
import com.duelyard.user.UserRepository;

/**
 * Darts board for this dataset (other games untouched, i.e. 1000 with no games):
 * <pre>
 *   a  1100  5 games   -> darts #1, global (1100+3000)/4 = 1025 -> #1
 *   b  1050  3 games   -> darts #2, global 1012.5 -> 1013      -> #2
 *   c  1050  2 games   -> darts #2 (tie), global 1013          -> #2 (tie)
 *   e   990  1 game, private profile -> #4, global 997.5 -> 998 -> #4
 *   f   980  1 game (name differs from b only where b has "_") -> #5
 *   d  1000  0 games   -> not ranked
 * </pre>
 */
@IntegrationTest
class LeaderboardTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired GameRepository games;
    @Autowired PlayerGameStatsRepository stats;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    private TestAccounts accounts;
    private String t;

    @BeforeEach
    void setUp() {
        accounts = new TestAccounts("lb");
        t = accounts.tag();
        player("a", "lb_a_" + t, 1100, 5, 0);
        player("b", "lb_b_" + t, 1050, 2, 1);
        player("c", "lb_c_" + t, 1050, 1, 1);
        player("d", "lb_d_" + t, 1000, 0, 0);
        player("e", "lb_e_" + t, 990, 0, 1);
        player("f", "lbxb_" + t, 980, 0, 1);
        jdbc.update("UPDATE users SET profile_visibility = 'PRIVATE' WHERE cognito_sub = ?", accounts.sub("e"));
    }

    @AfterEach
    void cleanUp() {
        accounts.cleanUp(jdbc);
    }

    private void player(String key, String username, int dartsRating, int wins, int losses) {
        tx.executeWithoutResult(s -> {
            User u = users.save(new User(accounts.sub(key), username, key + "@example.com"));
            games.findAll().forEach(g -> stats.save(new PlayerGameStats(u, g)));
        });
        jdbc.update("""
            UPDATE player_game_stats SET rating = ?, peak_rating = GREATEST(?, 1000), ranked_wins = ?, ranked_losses = ?
            WHERE user_id = (SELECT id FROM users WHERE cognito_sub = ?) AND game_id = (SELECT id FROM games WHERE slug = 'darts')
            """, dartsRating, dartsRating, wins, losses, accounts.sub(key));
    }

    @Test
    void gameBoardRanksByRatingWithSharedRanksForTies() throws Exception {
        mvc.perform(get("/api/leaderboards/darts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scopeName").value("Darts"))
            .andExpect(jsonPath("$.total").value(5))
            .andExpect(jsonPath("$.entries[*].username").value(org.hamcrest.Matchers.contains(
                "lb_a_" + t, "lb_b_" + t, "lb_c_" + t, "lb_e_" + t, "lbxb_" + t)))
            .andExpect(jsonPath("$.entries[*].rank").value(org.hamcrest.Matchers.contains(1, 2, 2, 4, 5)))
            .andExpect(jsonPath("$.entries[0].rating").value(1100))
            .andExpect(jsonPath("$.entries[0].wins").value(5))
            .andExpect(jsonPath("$.entries[0].gamesPlayed").value(5))
            .andExpect(jsonPath("$.viewer").value(nullValue()));
    }

    @Test
    void globalBoardAveragesAcrossAllGames() throws Exception {
        mvc.perform(get("/api/leaderboards/global"))
            .andExpect(jsonPath("$.scopeName").value("Overall"))
            .andExpect(jsonPath("$.total").value(5))
            .andExpect(jsonPath("$.entries[*].rating").value(org.hamcrest.Matchers.contains(1025, 1013, 1013, 998, 995)))
            .andExpect(jsonPath("$.entries[*].rank").value(org.hamcrest.Matchers.contains(1, 2, 2, 4, 5)));
    }

    @Test
    void playersWithoutRankedGamesAreNotListed() throws Exception {
        mvc.perform(get("/api/leaderboards/darts").param("search", "lb_d_" + t))
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.entries", hasSize(0)));
        mvc.perform(get("/api/leaderboards/mini-golf"))
            .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void searchKeepsTrueRanksAndTreatsUnderscoreLiterally() throws Exception {
        mvc.perform(get("/api/leaderboards/darts").param("search", "LB_E"))
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.entries[0].username").value("lb_e_" + t))
            .andExpect(jsonPath("$.entries[0].rank").value(4));
        // Unescaped, "_" would also match the "x" in "lbxb_".
        mvc.perform(get("/api/leaderboards/darts").param("search", "lb_b"))
            .andExpect(jsonPath("$.entries[*].username").value(org.hamcrest.Matchers.contains("lb_b_" + t)));
        mvc.perform(get("/api/leaderboards/darts").param("search", "%"))
            .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void pagesCarryTotalsEvenPastTheEnd() throws Exception {
        mvc.perform(get("/api/leaderboards/darts").param("size", "2").param("page", "1"))
            .andExpect(jsonPath("$.entries[*].username").value(org.hamcrest.Matchers.contains("lb_c_" + t, "lb_e_" + t)))
            .andExpect(jsonPath("$.total").value(5));
        mvc.perform(get("/api/leaderboards/darts").param("size", "2").param("page", "10"))
            .andExpect(jsonPath("$.entries", hasSize(0)))
            .andExpect(jsonPath("$.total").value(5));
        mvc.perform(get("/api/leaderboards/darts").param("size", "1000"))
            .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void signedInViewerGetsTheirOwnRow() throws Exception {
        mvc.perform(get("/api/leaderboards/darts").param("size", "1").with(accounts.as("e")))
            .andExpect(jsonPath("$.entries", hasSize(1)))
            .andExpect(jsonPath("$.viewer.username").value("lb_e_" + t))
            .andExpect(jsonPath("$.viewer.rank").value(4));
        mvc.perform(get("/api/leaderboards/darts").with(accounts.as("d")))
            .andExpect(jsonPath("$.viewer").value(nullValue()));
    }

    @Test
    void rejectsUnknownBoardsAndOverlongSearches() throws Exception {
        mvc.perform(get("/api/leaderboards/chess")).andExpect(status().isNotFound());
        mvc.perform(get("/api/leaderboards/darts").param("search", "x".repeat(21)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SEARCH_TOO_LONG"));
    }
}
