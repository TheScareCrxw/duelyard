package com.duelyard.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Helpers for integration tests whose data is committed (e.g. by provisioning, which runs in its
 * own transaction). Each test run uses a unique prefix and deletes its rows afterwards.
 */
public final class TestAccounts {

    private final String prefix;

    public TestAccounts(String tag) {
        this.prefix = "it-" + tag + "-" + UUID.randomUUID().toString().substring(0, 6) + "-";
    }

    /** A Cognito subject unique to this test run. */
    public String sub(String name) {
        return prefix + name;
    }

    /** Short unique suffix usable inside usernames (letters/digits only). */
    public String tag() {
        return prefix.substring(prefix.length() - 7, prefix.length() - 1).replace("-", "");
    }

    public RequestPostProcessor as(String name) {
        return jwt().jwt(j -> j.subject(sub(name)).claim("token_use", "access"));
    }

    /** Deletes this run's users and any matches they took part in. */
    public void cleanUp(JdbcTemplate jdbc) {
        String like = prefix + "%";
        String theirMatches = """
            SELECT mp.match_id FROM match_players mp JOIN users u ON u.id = mp.user_id WHERE u.cognito_sub LIKE ?""";
        // rating_history deliberately has no ON DELETE CASCADE from matches, so remove it first.
        jdbc.update("DELETE FROM rating_history WHERE match_id IN (" + theirMatches + ")", like);
        jdbc.update("""
            DELETE FROM matches WHERE id IN (
              SELECT mp.match_id FROM match_players mp JOIN users u ON u.id = mp.user_id WHERE u.cognito_sub LIKE ?)
            """, like);
        jdbc.update("DELETE FROM users WHERE cognito_sub LIKE ?", like);
    }
}
