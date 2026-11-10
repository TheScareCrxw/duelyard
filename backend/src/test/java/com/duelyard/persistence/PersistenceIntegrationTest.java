package com.duelyard.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.duelyard.game.Game;
import com.duelyard.game.GameMode;
import com.duelyard.game.GameRepository;
import com.duelyard.match.Match;
import com.duelyard.match.MatchEndReason;
import com.duelyard.match.MatchEvent;
import com.duelyard.match.MatchEventRepository;
import com.duelyard.match.MatchRepository;
import com.duelyard.match.MatchResult;
import com.duelyard.match.MatchStatus;
import com.duelyard.rating.PlayerGameStats;
import com.duelyard.rating.PlayerGameStatsRepository;
import com.duelyard.rating.RatingHistory;
import com.duelyard.rating.RatingHistoryRepository;
import com.duelyard.support.IntegrationTest;
import com.duelyard.user.Friendship;
import com.duelyard.user.FriendshipRepository;
import com.duelyard.user.User;
import com.duelyard.user.UserRepository;

import jakarta.persistence.EntityManager;

/** Verifies the Flyway schema, entity mappings and database-level invariants against real PostgreSQL. */
@IntegrationTest
@Transactional
class PersistenceIntegrationTest {

    @Autowired UserRepository users;
    @Autowired GameRepository games;
    @Autowired PlayerGameStatsRepository stats;
    @Autowired MatchRepository matches;
    @Autowired MatchEventRepository events;
    @Autowired RatingHistoryRepository ratingHistory;
    @Autowired FriendshipRepository friendships;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

    private User user(String name) {
        return users.saveAndFlush(new User("sub-" + name, name, name + "@example.com"));
    }

    private Game darts() {
        return games.findBySlug("darts").orElseThrow();
    }

    // --- games ---------------------------------------------------------------------------------

    @Test
    void migrationSeedsAllFourGames() {
        assertThat(games.findAll()).extracting(Game::getSlug)
            .containsExactlyInAnyOrder("darts", "mini-golf", "eight-ball", "rocket-launcher");
    }

    // --- users ---------------------------------------------------------------------------------

    @Test
    void userGetsIdTimestampsAndDefaults() {
        User u = user("alice");
        assertThat(u.getId()).isNotNull();
        assertThat(u.getCreatedAt()).isNotNull();
        assertThat(u.getProfileVisibility().name()).isEqualTo("PUBLIC");
        assertThat(users.findByCognitoSub("sub-alice")).contains(u);
    }

    @Test
    void usernamesAreUniqueCaseInsensitively() {
        user("Alice");
        assertThat(users.existsByUsernameIgnoreCase("ALICE")).isTrue();
        assertThatThrownBy(() -> users.saveAndFlush(new User("other-sub", "alice", "x@example.com")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void oneAccountPerCognitoIdentity() {
        user("bob");
        assertThatThrownBy(() -> users.saveAndFlush(new User("sub-bob", "bob2", "b@example.com")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // Each violation aborts the PostgreSQL transaction, so one violation per test.
    @Test
    void usernameWithIllegalCharactersIsRejectedByDatabase() {
        assertThatThrownBy(() -> users.saveAndFlush(new User("sub-x", "no spaces!", "x@example.com")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void tooShortUsernameIsRejectedByDatabase() {
        assertThatThrownBy(() -> users.saveAndFlush(new User("sub-y", "ab", "y@example.com")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- stats ---------------------------------------------------------------------------------

    @Test
    void statsStartAt1000AndAreUniquePerUserAndGame() {
        User u = user("carol");
        PlayerGameStats s = stats.saveAndFlush(new PlayerGameStats(u, darts()));
        assertThat(s.getRating()).isEqualTo(1000);
        assertThat(s.getPeakRating()).isEqualTo(1000);
        assertThat(s.getRankedGamesPlayed()).isZero();
        assertThat(stats.findByUserIdAndGameId(u.getId(), darts().getId())).isPresent();

        assertThatThrownBy(() -> stats.saveAndFlush(new PlayerGameStats(u, darts())))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- matches -------------------------------------------------------------------------------

    private Match completedRankedMatch(User a, User b) {
        Match m = new Match(darts(), GameMode.RANKED);
        m.addPlayer(a, 0);
        m.addPlayer(b, 1);
        m.start(now);
        m.end(MatchStatus.COMPLETED, MatchEndReason.NORMAL, now);
        m.recordResult(a.getId(), MatchResult.WIN, 501);
        m.recordResult(b.getId(), MatchResult.LOSS, 120);
        return matches.saveAndFlush(m);
    }

    @Test
    void matchPersistsWithPlayersAndUuidV7Id() {
        User a = user("dave");
        User b = user("erin");
        Match saved = completedRankedMatch(a, b);
        em.clear();

        Match loaded = matches.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getId().version()).isEqualTo(7);
        assertThat(loaded.getStatus()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(loaded.getPlayers()).hasSize(2);
        assertThat(loaded.getPlayers().getFirst().getResult()).isEqualTo(MatchResult.WIN);
        assertThat(loaded.getPlayers().getFirst().getScore()).isEqualTo(501);
    }

    @Test
    void matchLifecycleRejectsInvalidTransitions() {
        User a = user("frank");
        Match m = new Match(darts(), GameMode.CASUAL);
        m.addPlayer(a, 0);
        assertThatThrownBy(() -> m.addPlayer(a, 1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> m.end(MatchStatus.COMPLETED, MatchEndReason.NORMAL, now))
            .isInstanceOf(IllegalStateException.class);

        m.end(MatchStatus.CANCELLED, null, now);
        assertThatThrownBy(() -> m.end(MatchStatus.ABANDONED, null, now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> m.markRatingsApplied(now)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ratingsCanBeMarkedAppliedOnlyOnce() {
        Match m = completedRankedMatch(user("gina"), user("hank"));
        m.markRatingsApplied(now);
        assertThatThrownBy(() -> m.markRatingsApplied(now)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void databaseRejectsEndedTimestampOnActiveMatch() {
        Match m = matches.saveAndFlush(new Match(darts(), GameMode.CASUAL));
        assertThatThrownBy(() -> jdbc.update("UPDATE matches SET ended_at = now() WHERE id = ?", m.getId()))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- rating history ------------------------------------------------------------------------

    @Test
    void ratingHistoryAllowsOneEntryPerPlayerPerMatch() {
        User a = user("ivan");
        User b = user("judy");
        Match m = completedRankedMatch(a, b);

        RatingHistory entry = ratingHistory.saveAndFlush(new RatingHistory(a, darts(), m, 1000, 1016));
        assertThat(entry.getDelta()).isEqualTo(16);
        assertThat(ratingHistory.existsByMatchId(m.getId())).isTrue();

        assertThatThrownBy(() -> ratingHistory.saveAndFlush(new RatingHistory(a, darts(), m, 1016, 1032)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- match events --------------------------------------------------------------------------

    @Test
    void matchEventPayloadIsStoredAsQueryableJsonb() {
        User a = user("kate");
        Match m = matches.saveAndFlush(new Match(darts(), GameMode.CASUAL));
        events.saveAndFlush(new MatchEvent(m, 0, a, "THROW", "{\"segment\": 20, \"multiplier\": 3}"));
        em.clear();

        Integer segment = jdbc.queryForObject(
            "SELECT (payload->>'segment')::int FROM match_events WHERE match_id = ?", Integer.class, m.getId());
        assertThat(segment).isEqualTo(20);
        assertThat(events.findByMatchIdOrderBySequenceAsc(m.getId()))
            .singleElement()
            .satisfies(e -> assertThat(e.getPayload()).contains("\"multiplier\"").contains("3"));
    }

    @Test
    void duplicateEventSequenceIsRejected() {
        Match m = matches.saveAndFlush(new Match(darts(), GameMode.CASUAL));
        events.saveAndFlush(new MatchEvent(m, 0, null, "MATCH_CREATED", null));
        assertThatThrownBy(() -> events.saveAndFlush(new MatchEvent(m, 0, null, "MATCH_CREATED", null)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- friendships ---------------------------------------------------------------------------

    @Test
    void friendshipIsUniquePerUnorderedPair() {
        User a = user("liam");
        User b = user("mona");
        friendships.saveAndFlush(new Friendship(a, b));
        assertThatThrownBy(() -> friendships.saveAndFlush(new Friendship(b, a)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
