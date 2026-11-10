package com.duelyard.leaderboard;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Leaderboard read model in SQL. Ranks are computed over the full board with a window function
 * before any search filter or paging, so a filtered or paged row still shows its true rank.
 *
 * <p>Computed on demand: fine at current scale. If it becomes hot, the same queries can back a
 * materialized view or a Redis sorted set without changing the API.
 */
@Repository
class LeaderboardRepository {

    /** Rows of a ranked board: (user_id, username, avatar_url, rating, wins, losses, draws, games_played). */
    private static final String GAME_BOARD = """
        SELECT u.id AS user_id, u.username, u.avatar_url, s.rating,
               s.ranked_wins AS wins, s.ranked_losses AS losses, s.ranked_draws AS draws,
               s.ranked_wins + s.ranked_losses + s.ranked_draws AS games_played
        FROM player_game_stats s
        JOIN users u ON u.id = s.user_id
        WHERE s.game_id = :gameId
          AND s.ranked_wins + s.ranked_losses + s.ranked_draws >= :minGames
        """;

    /** Global board: average rating over every game (an unplayed game counts as 1000), ranked totals. */
    private static final String GLOBAL_BOARD = """
        SELECT * FROM (
          SELECT u.id AS user_id, u.username, u.avatar_url,
                 ROUND(AVG(COALESCE(s.rating, 1000)))::int AS rating,
                 COALESCE(SUM(s.ranked_wins), 0)::int AS wins,
                 COALESCE(SUM(s.ranked_losses), 0)::int AS losses,
                 COALESCE(SUM(s.ranked_draws), 0)::int AS draws,
                 COALESCE(SUM(s.ranked_wins + s.ranked_losses + s.ranked_draws), 0)::int AS games_played
          FROM users u
          CROSS JOIN games g
          LEFT JOIN player_game_stats s ON s.user_id = u.id AND s.game_id = g.id
          GROUP BY u.id, u.username, u.avatar_url
        ) totals
        WHERE games_played >= :minGames
        """;

    private static final RowMapper<LeaderboardRow> ROW = (rs, i) -> new LeaderboardRow(
        rs.getLong("rank"), rs.getLong("user_id"), rs.getString("username"), rs.getString("avatar_url"),
        rs.getInt("rating"), rs.getInt("wins"), rs.getInt("losses"), rs.getInt("draws"), rs.getInt("games_played"));

    private final NamedParameterJdbcTemplate jdbc;

    LeaderboardRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record Page(List<LeaderboardRow> rows, long total) {
    }

    /**
     * @param gameId null for the global board
     * @param search case-insensitive username substring, or null
     */
    Page page(Long gameId, int minGames, String search, int limit, int offset) {
        MapSqlParameterSource params = params(gameId, minGames).addValue("limit", limit).addValue("offset", offset);
        String filter = "";
        if (search != null) {
            filter = "WHERE lower(username) LIKE :pattern ESCAPE '\\'";
            params.addValue("pattern", "%" + escapeLike(search.toLowerCase()) + "%");
        }
        String sql = ranked(gameId) + """
            SELECT *, COUNT(*) OVER () AS total FROM ranked %s
            ORDER BY rank, games_played DESC, user_id
            LIMIT :limit OFFSET :offset
            """.formatted(filter);

        long[] total = {0};
        List<LeaderboardRow> rows = jdbc.query(sql, params, (rs, i) -> {
            total[0] = rs.getLong("total");
            return ROW.mapRow(rs, i);
        });
        // An offset past the end returns no rows (and so no total); count separately in that case.
        if (rows.isEmpty() && offset > 0) {
            Long count = jdbc.queryForObject(ranked(gameId) + "SELECT COUNT(*) FROM ranked " + filter, params, Long.class);
            total[0] = count == null ? 0 : count;
        }
        return new Page(rows, total[0]);
    }

    Optional<LeaderboardRow> rowFor(Long gameId, int minGames, long userId) {
        MapSqlParameterSource params = params(gameId, minGames).addValue("userId", userId);
        return jdbc.query(ranked(gameId) + "SELECT * FROM ranked WHERE user_id = :userId", params, ROW)
            .stream().findFirst();
    }

    private static String ranked(Long gameId) {
        return "WITH board AS (" + (gameId == null ? GLOBAL_BOARD : GAME_BOARD) + "), "
            + "ranked AS (SELECT *, RANK() OVER (ORDER BY rating DESC) AS rank FROM board) ";
    }

    private static MapSqlParameterSource params(Long gameId, int minGames) {
        MapSqlParameterSource params = new MapSqlParameterSource("minGames", minGames);
        if (gameId != null) {
            params.addValue("gameId", gameId);
        }
        return params;
    }

    /** Usernames contain underscores, a LIKE wildcard; match them literally. */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
