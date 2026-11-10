package com.duelyard.multiplayer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
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

/** With a 300 ms turn limit, a match where nobody moves ends in a TIMEOUT forfeit after 3 missed turns. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "app.realtime.countdown=100ms",
    "app.realtime.turn-limit-override=300ms",
    "app.matchmaking.tick=100",
})
@Import(EmbeddedPostgresTestConfiguration.class)
class TurnTimeoutIntegrationTest {

    @LocalServerPort int port;
    @Autowired TicketService tickets;
    @Autowired UserRepository users;
    @Autowired GameRepository games;
    @Autowired PlayerGameStatsRepository stats;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    private final TestAccounts accounts = new TestAccounts("to");

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

    @Test
    void repeatedTimeoutsForfeit() throws Exception {
        Duration wait = Duration.ofSeconds(10);
        try (WsTestClient a = WsTestClient.connect(port, tickets.issue(createUser("a")));
             WsTestClient b = WsTestClient.connect(port, tickets.issue(createUser("b")))) {
            a.send(Map.of("type", "queue.join", "game", "rocket-launcher", "mode", "CASUAL"));
            b.send(Map.of("type", "queue.join", "game", "rocket-launcher", "mode", "CASUAL"));

            JsonNode timedOut = a.await("match.events", wait);
            assertThat(timedOut.path("timedOut").asBoolean()).isTrue();

            JsonNode ended = a.await("match.ended", wait);
            assertThat(ended.path("reason").asString()).isEqualTo("TIMEOUT");
            assertThat(ended.path("results").get(0).path("result").asString()).isEqualTo("LOSS");   // seat 0 timed out first
        }
    }
}
