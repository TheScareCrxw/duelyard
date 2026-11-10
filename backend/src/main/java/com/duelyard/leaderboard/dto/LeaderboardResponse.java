package com.duelyard.leaderboard.dto;

import java.util.List;

/**
 * A page of a leaderboard.
 *
 * @param scope   "global" or a game slug
 * @param total   number of rows matching the search (all ranked players without one)
 * @param viewer  the signed-in caller's own row, if they're ranked on this board; null otherwise
 */
public record LeaderboardResponse(
        String scope,
        String scopeName,
        String search,
        int page,
        int size,
        long total,
        List<Entry> entries,
        Entry viewer) {

    public record Entry(
            long rank,
            String username,
            String avatarUrl,
            int rating,
            int wins,
            int losses,
            int draws,
            int gamesPlayed) {
    }
}
