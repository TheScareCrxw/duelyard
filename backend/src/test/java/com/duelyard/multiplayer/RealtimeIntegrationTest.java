package com.duelyard.multiplayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.duelyard.game.GameRepository;
import com.duelyard.rating.PlayerGameStats;
import com.duelyard.rating.PlayerGameStatsRepository;
import com.duelyard.support.EmbeddedPostgresTestConfiguration;
import com.duelyard.support.TestAccounts;
import com.duelyard.support.WsTestClient;
import com.duelyard.user.User;
import com.duelyard.user.UserRepository;

import tools.jackson.databind.JsonNode;

/** Two real WebSocket clients against the running server: matchmaking, a full game, disconnects. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "app.realtime.countdown=200ms",
    "app.realtime.disconnect-grace=1s",
    "app.matchmaking.tick=100",
})
@Import({EmbeddedPostgresTestConfiguration.class, RealtimeIntegrationTest.SeededRandom.class})
class RealtimeIntegrationTest {

    @TestConfiguration
    static class SeededRandom {
        @Bean
        @Primary
        MatchRandomFactory seededMatchRandom() {
            Random seeds = new Random(42);
            return () -> new Random(seeds.nextLong());
        }
    }

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final Map<String, Object> T20 = Map.of("x", 0, "y", 103, "release", 0.5);

    @LocalServerPort int port;
    @Autowired TicketService tickets;
    @Autowired UserRepository users;
    @Autowired GameRepository games;
    @Autowired PlayerGameStatsRepository stats;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    private TestAccounts accounts;
    private long alice;
    private long bob;

    @BeforeEach
    void setUp() {
        accounts = new TestAccounts("ws");
        alice = createUser("alice");
        bob = createUser("bob");
    }

    @AfterEach
    void cleanUp() {
        accounts.cleanUp(jdbc);
    }

    private long createUser(String name) {
        return tx.execute(s -> {
            User u = users.save(new User(accounts.sub(name), name + "_" + accounts.tag(), name + "@example.com"));
            games.findAll().forEach(g -> stats.save(new PlayerGameStats(u, g)));
            return u.getId();
        });
    }

    private WsTestClient connect(long userId) throws Exception {
        WsTestClient client = WsTestClient.connect(port, tickets.issue(userId));
        client.await("welcome", WAIT);
        return client;
    }

    /** Both queue for the same game/mode; returns the match id once both have been told. */
    private UUID pair(WsTestClient a, WsTestClient b, String game, String mode) throws Exception {
        a.send(Map.of("type", "queue.join", "game", game, "mode", mode));
        b.send(Map.of("type", "queue.join", "game", game, "mode", mode));
        JsonNode foundA = a.await("match.found", WAIT);
        JsonNode foundB = b.await("match.found", WAIT);
        assertThat(foundA.path("matchId")).isEqualTo(foundB.path("matchId"));
        assertThat(foundA.path("opponent").path("username").asString()).isEqualTo("bob_" + accounts.tag());
        return UUID.fromString(foundA.path("matchId").asString());
    }

    /** Waits for the PLAYING state; returns {seat of a, seat of b, turn seat}. */
    private int[] awaitPlaying(WsTestClient a, WsTestClient b) throws Exception {
        JsonNode sa = awaitPhase(a, "PLAYING");
        JsonNode sb = awaitPhase(b, "PLAYING");
        return new int[] {sa.path("yourSeat").asInt(), sb.path("yourSeat").asInt(), sa.path("turnSeat").asInt()};
    }

    private static JsonNode awaitPhase(WsTestClient c, String phase) throws Exception {
        while (true) {
            JsonNode state = c.await("match.state", WAIT).path("state");
            if (phase.equals(state.path("phase").asString())) return state;
        }
    }

    @Test
    void fullRankedDartsMatchFromQueueToRatingUpdate() throws Exception {
        try (WsTestClient a = connect(alice); WsTestClient b = connect(bob)) {
            UUID matchId = pair(a, b, "darts", "RANKED");
            int[] seats = awaitPlaying(a, b);
            WsTestClient[] bySeat = new WsTestClient[2];
            bySeat[seats[0]] = a;
            bySeat[seats[1]] = b;

            // Each player throws at T20 whenever the server says it's their turn, until the server ends it.
            bySeat[seats[2]].send(Map.of("type", "match.action", "matchId", matchId.toString(), "input", T20));
            JsonNode ended = null;
            int moves = 1;
            long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
            while (ended == null && System.nanoTime() < deadline) {
                for (int seat = 0; seat < 2 && ended == null; seat++) {
                    JsonNode msg = bySeat[seat].poll(Duration.ofMillis(50));
                    if (msg == null) continue;
                    String type = msg.path("type").asString();
                    if (type.equals("match.ended")) ended = msg;
                    if (type.equals("error")) throw new AssertionError("Server error: " + msg);
                    if (type.equals("match.events") && msg.path("state").path("turnSeat").asInt() == seat) {
                        bySeat[seat].send(Map.of("type", "match.action", "matchId", matchId.toString(), "input", T20));
                        moves++;
                    }
                }
            }
            assertThat(ended).as("match ended").isNotNull();
            assertThat(ended.path("reason").asString()).isEqualTo("NORMAL");
            JsonNode results = ended.path("results");
            assertThat(results.get(0).path("ratingAfter").isNull()).isFalse();
            int after0 = results.get(0).path("ratingAfter").asInt();
            int after1 = results.get(1).path("ratingAfter").asInt();
            assertThat(after0 + after1).isEqualTo(2000);   // zero-sum

            assertThat(jdbc.queryForObject("SELECT status FROM matches WHERE id = ?", String.class, matchId)).isEqualTo("COMPLETED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM rating_history WHERE match_id = ?", Integer.class, matchId)).isEqualTo(2);
            // One audit row per accepted move, plus start and end.
            assertThat(jdbc.queryForObject("SELECT count(*) FROM match_events WHERE match_id = ?", Integer.class, matchId))
                .isEqualTo(moves + 2);
        }
    }

    @Test
    void movesOutOfTurnAreRejectedAndChangeNothing() throws Exception {
        try (WsTestClient a = connect(alice); WsTestClient b = connect(bob)) {
            UUID matchId = pair(a, b, "darts", "CASUAL");
            int[] seats = awaitPlaying(a, b);
            WsTestClient waiting = seats[0] == seats[2] ? b : a;
            waiting.send(Map.of("type", "match.action", "matchId", matchId.toString(), "input", T20));
            assertThat(waiting.await("error", WAIT).path("code").asString()).isEqualTo("NOT_YOUR_TURN");

            WsTestClient mover = waiting == a ? b : a;
            mover.send(Map.of("type", "match.action", "matchId", matchId.toString(), "input", Map.of("x", "lots")));
            assertThat(mover.await("error", WAIT).path("code").asString()).isEqualTo("INVALID_INPUT");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM match_events WHERE match_id = ? AND event_type = 'action'",
                Integer.class, matchId)).isZero();
        }
    }

    @Test
    void disconnectedPlayerForfeitsAfterGracePeriod() throws Exception {
        WsTestClient a = connect(alice);
        try (WsTestClient b = connect(bob)) {
            UUID matchId = pair(a, b, "darts", "RANKED");
            awaitPlaying(a, b);
            a.close();

            JsonNode notice = b.await("opponent.connection", WAIT);
            assertThat(notice.path("connected").asBoolean()).isFalse();
            JsonNode ended = b.await("match.ended", WAIT);
            assertThat(ended.path("reason").asString()).isEqualTo("DISCONNECT");
            JsonNode bobResult = ended.path("results").get(0).path("username").asString().startsWith("bob")
                ? ended.path("results").get(0) : ended.path("results").get(1);
            assertThat(bobResult.path("result").asString()).isEqualTo("WIN");
            assertThat(jdbc.queryForObject("SELECT end_reason FROM matches WHERE id = ?", String.class, matchId)).isEqualTo("DISCONNECT");
        }
    }

    @Test
    void reconnectingWithinGraceResumesTheMatch() throws Exception {
        WsTestClient a = connect(alice);
        try (WsTestClient b = connect(bob)) {
            UUID matchId = pair(a, b, "darts", "CASUAL");
            awaitPlaying(a, b);
            a.close();
            b.await("opponent.connection", WAIT);

            try (WsTestClient again = WsTestClient.connect(port, tickets.issue(alice))) {
                JsonNode welcome = again.await("welcome", WAIT);
                assertThat(welcome.path("activeMatchId").asString()).isEqualTo(matchId.toString());
                assertThat(again.await("match.state", WAIT).path("state").path("phase").asString()).isEqualTo("PLAYING");
                assertThat(b.await("opponent.connection", WAIT).path("connected").asBoolean()).isTrue();
                Thread.sleep(1500);   // past the grace period: the match must still be running
                assertThat(jdbc.queryForObject("SELECT status FROM matches WHERE id = ?", String.class, matchId)).isEqualTo("IN_PROGRESS");
            }
        }
    }

    @Test
    void queueRequestsAreValidated() throws Exception {
        try (WsTestClient a = connect(alice); WsTestClient b = connect(bob)) {
            a.send(Map.of("type", "queue.join", "game", "chess", "mode", "RANKED"));
            assertThat(a.await("error", WAIT).path("code").asString()).isEqualTo("INVALID_QUEUE");

            pair(a, b, "darts", "CASUAL");
            a.send(Map.of("type", "queue.join", "game", "darts", "mode", "RANKED"));
            assertThat(a.await("error", WAIT).path("code").asString()).isEqualTo("ALREADY_IN_MATCH");

            a.send(Map.of("type", "nonsense"));
            assertThat(a.await("error", WAIT).path("code").asString()).isEqualTo("UNKNOWN_TYPE");
        }
    }

    @Test
    void leavingTheQueueIsConfirmed() throws Exception {
        try (WsTestClient a = connect(alice)) {
            a.send(Map.of("type", "queue.join", "game", "eight-ball", "mode", "RANKED"));
            assertThat(a.await("queue.status", WAIT).path("ratingRange").asInt()).isEqualTo(100);
            a.send(Map.of("type", "queue.leave"));
            assertThat(a.await("queue.left", WAIT).path("reason").asString()).isEqualTo("CANCELLED");
        }
    }

    @Test
    void connectionsRequireAValidSingleUseTicket() throws Exception {
        assertThatThrownBy(() -> WsTestClient.connect(port, "forged")).isInstanceOf(Exception.class);
        String ticket = tickets.issue(alice);
        try (WsTestClient first = WsTestClient.connect(port, ticket)) {
            assertThat(first.isOpen()).isTrue();
        }
        assertThatThrownBy(() -> WsTestClient.connect(port, ticket)).isInstanceOf(Exception.class);
    }
}
