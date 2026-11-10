package com.duelyard.leaderboard;

/** One ranked player on a leaderboard. Ties share a rank (competition ranking: 1, 2, 2, 4). */
public record LeaderboardRow(
        long rank,
        long userId,
        String username,
        String avatarUrl,
        int rating,
        int wins,
        int losses,
        int draws,
        int gamesPlayed) {
}
