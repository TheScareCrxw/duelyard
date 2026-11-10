package com.duelyard.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.duelyard.game.GameMode;
import com.duelyard.game.GameRepository;
import com.duelyard.rating.PlayerGameStats;
import com.duelyard.rating.PlayerGameStatsRepository;
import com.duelyard.rating.RatingHistoryRepository;
import com.duelyard.support.IntegrationTest;
import com.duelyard.support.TestAccounts;
import com.duelyard.user.User;
import com.duelyard.user.UserRepository;

/** Match completion and rating updates against real PostgreSQL, with committed transactions. */
@IntegrationTest
class MatchCompletionServiceTest {

    @Autowired MatchCompletionService completion;
    @Autowired MatchRepository matches;
    @Autowired UserRepository users;
    @Autowired GameRepository games;
    @Autowired PlayerGameStatsRepository stats;
    @Autowired RatingHistoryRepository history;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    private TestAccounts accounts;
    private Long alice;
    private Long bob;

    @BeforeEach
    void setUp() {
        accounts = new TestAccounts("elo");
        alice = createUser("alice", true);
        bob = createUser("bob", true);
    }

    @AfterEach
    void cleanUp() {
        accounts.cleanUp(jdbc);
    }

    private Long createUser(String name, boolean withStats) {
        return tx.execute(s -> {
            User u = users.save(new User(accounts.sub(name), name + "_" + accounts.tag(), name + "@example.com"));
            if (withStats) games.findAll().forEach(g -> stats.save(new PlayerGameStats(u, g)));
            return u.getId();
        });
    }

    private UUID startMatch(GameMode mode, Long... players) {
        return tx.execute(s -> {
            Match m = new Match(games.findBySlug("darts").orElseThrow(), mode);
            for (int seat = 0; seat < players.length; seat++) m.addPlayer(users.getReferenceById(players[seat]), seat);
            m.start(Instant.now());
            return matches.save(m).getId();
        });
    }

    private PlayerGameStats dartsStats(Long userId) {
        return stats.findByUserIdAndGameId(userId, games.findBySlug("darts").orElseThrow().getId()).orElseThrow();
    }

    private Match load(UUID id) {
        return tx.execute(s -> {
            Match m = matches.findById(id).orElseThrow();
            m.getPlayers().forEach(p -> p.getUser().getId());
            return m;
        });
    }

    @Test
    void rankedWinUpdatesRatingsRecordsHistoryAndMatch() {
        UUID id = startMatch(GameMode.RANKED, alice, bob);

        MatchCompletion result = completion.complete(id,
            List.of(new PlayerOutcome(alice, MatchResult.WIN, 501), PlayerOutcome.loss(bob)), MatchEndReason.NORMAL);

        assertThat(result.alreadyCompleted()).isFalse();
        assertThat(result.players()).extracting(MatchCompletion.PlayerChange::ratingAfter).containsExactly(1016, 984);

        PlayerGameStats a = dartsStats(alice);
        PlayerGameStats b = dartsStats(bob);
        assertThat(a.getRating()).isEqualTo(1016);
        assertThat(a.getPeakRating()).isEqualTo(1016);
        assertThat(a.getRankedWins()).isEqualTo(1);
        assertThat(b.getRating()).isEqualTo(984);
        assertThat(b.getPeakRating()).isEqualTo(1000);
        assertThat(b.getRankedLosses()).isEqualTo(1);

        Match m = load(id);
        assertThat(m.getStatus()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(m.getRatingsAppliedAt()).isNotNull();
        assertThat(m.player(alice).getScore()).isEqualTo(501);
        assertThat(m.player(bob).getRatingBefore()).isEqualTo(1000);
        assertThat(m.player(bob).getRatingAfter()).isEqualTo(984);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rating_history WHERE match_id = ?", Integer.class, id))
            .isEqualTo(2);
    }

    @Test
    void ratingsEvolveFromCurrentValues() {
        completion.complete(startMatch(GameMode.RANKED, alice, bob),
            List.of(PlayerOutcome.win(alice), PlayerOutcome.loss(bob)), MatchEndReason.NORMAL);
        // Now 1016 vs 984: the underdog (bob) winning gains 32 * (1 - 0.4540) = 17.47 -> 17
        completion.complete(startMatch(GameMode.RANKED, alice, bob),
            List.of(PlayerOutcome.loss(alice), PlayerOutcome.win(bob)), MatchEndReason.NORMAL);

        assertThat(dartsStats(alice).getRating()).isEqualTo(999);
        assertThat(dartsStats(alice).getPeakRating()).isEqualTo(1016);
        assertThat(dartsStats(bob).getRating()).isEqualTo(1001);
    }

    @Test
    void rankedDrawBetweenEqualsChangesRecordsNotRatings() {
        UUID id = startMatch(GameMode.RANKED, alice, bob);
        completion.complete(id, List.of(PlayerOutcome.draw(alice), PlayerOutcome.draw(bob)), MatchEndReason.NORMAL);

        assertThat(dartsStats(alice).getRating()).isEqualTo(1000);
        assertThat(dartsStats(alice).getRankedDraws()).isEqualTo(1);
        assertThat(dartsStats(bob).getRankedDraws()).isEqualTo(1);
        assertThat(load(id).getRatingsAppliedAt()).isNotNull();
    }

    @Test
    void casualMatchesNeverAffectRatings() {
        UUID id = startMatch(GameMode.CASUAL, alice, bob);
        MatchCompletion result = completion.complete(id,
            List.of(PlayerOutcome.win(alice), PlayerOutcome.loss(bob)), MatchEndReason.NORMAL);

        assertThat(result.players()).allSatisfy(p -> assertThat(p.ratingAfter()).isNull());
        assertThat(dartsStats(alice).getRating()).isEqualTo(1000);
        assertThat(dartsStats(alice).getCasualWins()).isEqualTo(1);
        assertThat(dartsStats(alice).getRankedWins()).isZero();
        assertThat(dartsStats(bob).getCasualLosses()).isEqualTo(1);
        assertThat(history.existsByMatchId(id)).isFalse();
        assertThat(load(id).getRatingsAppliedAt()).isNull();
    }

    @Test
    void completingTwiceAppliesRatingsOnce() {
        UUID id = startMatch(GameMode.RANKED, alice, bob);
        List<PlayerOutcome> outcome = List.of(PlayerOutcome.win(alice), PlayerOutcome.loss(bob));

        completion.complete(id, outcome, MatchEndReason.NORMAL);
        MatchCompletion second = completion.complete(id, outcome, MatchEndReason.NORMAL);
        // Even a contradictory second report changes nothing.
        completion.complete(id, List.of(PlayerOutcome.loss(alice), PlayerOutcome.win(bob)), MatchEndReason.NORMAL);

        assertThat(second.alreadyCompleted()).isTrue();
        assertThat(dartsStats(alice).getRating()).isEqualTo(1016);
        assertThat(dartsStats(alice).getRankedGamesPlayed()).isEqualTo(1);
    }

    @Test
    void concurrentCompletionsApplyExactlyOnce() throws Exception {
        UUID id = startMatch(GameMode.RANKED, alice, bob);
        List<PlayerOutcome> outcome = List.of(PlayerOutcome.win(alice), PlayerOutcome.loss(bob));
        int threads = 6;
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            start.await();
            return completion.complete(id, outcome, MatchEndReason.NORMAL).alreadyCompleted();
        };

        List<Future<Boolean>> results;
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            results = java.util.stream.IntStream.range(0, threads).mapToObj(i -> pool.submit(attempt)).toList();
            start.countDown();
        }

        long applied = 0;
        for (Future<Boolean> f : results) if (!f.get()) applied++;
        assertThat(applied).isEqualTo(1);
        assertThat(dartsStats(alice).getRating()).isEqualTo(1016);
        assertThat(dartsStats(bob).getRating()).isEqualTo(984);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rating_history WHERE match_id = ?", Integer.class, id))
            .isEqualTo(2);
    }

    @Test
    void rejectsInconsistentOrIncompleteOutcomesAndLeavesMatchUntouched() {
        UUID id = startMatch(GameMode.RANKED, alice, bob);
        Long carol = createUser("carol", true);

        assertThatThrownBy(() -> completion.complete(id, List.of(PlayerOutcome.win(alice), PlayerOutcome.win(bob)), MatchEndReason.NORMAL))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Inconsistent");
        assertThatThrownBy(() -> completion.complete(id, List.of(PlayerOutcome.win(alice)), MatchEndReason.NORMAL))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> completion.complete(id, List.of(PlayerOutcome.win(alice), PlayerOutcome.loss(carol)), MatchEndReason.NORMAL))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> completion.complete(id, List.of(PlayerOutcome.win(alice), PlayerOutcome.loss(alice)), MatchEndReason.NORMAL))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> completion.complete(id, List.of(PlayerOutcome.draw(alice), PlayerOutcome.loss(bob)), MatchEndReason.NORMAL))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(load(id).getStatus()).isEqualTo(MatchStatus.IN_PROGRESS);
        assertThat(dartsStats(alice).getRating()).isEqualTo(1000);
    }

    @Test
    void matchesThatHaventStartedCantBeCompleted() {
        UUID id = tx.execute(s -> {
            Match m = new Match(games.findBySlug("darts").orElseThrow(), GameMode.RANKED);
            m.addPlayer(users.getReferenceById(alice), 0);
            m.addPlayer(users.getReferenceById(bob), 1);
            return matches.save(m).getId();
        });
        assertThatThrownBy(() -> completion.complete(id, List.of(PlayerOutcome.win(alice), PlayerOutcome.loss(bob)), MatchEndReason.NORMAL))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void abandonedMatchesChangeNothingAndCantBeCompletedLater() {
        UUID id = startMatch(GameMode.RANKED, alice, bob);
        completion.abandon(id, MatchEndReason.DISCONNECT);

        assertThat(load(id).getStatus()).isEqualTo(MatchStatus.ABANDONED);
        assertThatThrownBy(() -> completion.complete(id, List.of(PlayerOutcome.win(alice), PlayerOutcome.loss(bob)), MatchEndReason.NORMAL))
            .isInstanceOf(IllegalStateException.class);
        assertThat(dartsStats(alice).getRating()).isEqualTo(1000);
        assertThat(dartsStats(alice).getRankedGamesPlayed()).isZero();
    }

    @Test
    void missingStatsRowIsCreatedOnFirstRatedMatch() {
        Long newcomer = createUser("dave", false);
        completion.complete(startMatch(GameMode.RANKED, newcomer, bob),
            List.of(PlayerOutcome.win(newcomer), PlayerOutcome.loss(bob)), MatchEndReason.NORMAL);

        assertThat(dartsStats(newcomer).getRating()).isEqualTo(1016);
    }

    @Test
    void ratingHistoryEndpointReturnsOrderedPoints() throws Exception {
        completion.complete(startMatch(GameMode.RANKED, alice, bob),
            List.of(PlayerOutcome.win(alice), PlayerOutcome.loss(bob)), MatchEndReason.NORMAL);
        completion.complete(startMatch(GameMode.RANKED, alice, bob),
            List.of(PlayerOutcome.loss(alice), PlayerOutcome.win(bob)), MatchEndReason.NORMAL);
        String name = "alice_" + accounts.tag();

        mvc.perform(get("/api/players/" + name + "/rating-history").param("game", "darts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.initialRating").value(1000))
            .andExpect(jsonPath("$.points", hasSize(2)))
            .andExpect(jsonPath("$.points[0].rating").value(1016))
            .andExpect(jsonPath("$.points[0].change").value(16))
            .andExpect(jsonPath("$.points[1].rating").value(999))
            .andExpect(jsonPath("$.points[1].change").value(-17));
        mvc.perform(get("/api/players/" + name + "/rating-history").param("game", "darts").param("limit", "1"))
            .andExpect(jsonPath("$.points", hasSize(1)))
            .andExpect(jsonPath("$.initialRating").value(1016));
        mvc.perform(get("/api/players/" + name + "/rating-history").param("game", "chess"))
            .andExpect(status().isNotFound());

        jdbc.update("UPDATE users SET profile_visibility = 'PRIVATE' WHERE id = ?", alice);
        mvc.perform(get("/api/players/" + name + "/rating-history").param("game", "darts"))
            .andExpect(status().isForbidden());
    }
}
