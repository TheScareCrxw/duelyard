package com.duelyard.user.dto;

import java.time.Instant;
import java.util.List;

import com.duelyard.match.dto.MatchSummaryResponse;
import com.duelyard.rating.dto.GameRatingResponse;

/**
 * A player's public profile. Never includes email or identity-provider details.
 *
 * @param self       true when the viewer is this player
 * @param restricted true when the profile is private and the viewer isn't its owner; then
 *                   {@code summary}, {@code ratings} and {@code recentMatches} are null
 */
public record PlayerProfileResponse(
        String username,
        String avatarUrl,
        Instant memberSince,
        String profileVisibility,
        boolean self,
        boolean restricted,
        Summary summary,
        List<GameRatingResponse> ratings,
        List<MatchSummaryResponse> recentMatches) {

    /**
     * Totals across all games, ranked and casual.
     *
     * @param winRate wins / games played (0..1), or null before the first game
     */
    public record Summary(int gamesPlayed, int wins, int losses, int draws, Double winRate, int highestRating) {
    }
}
