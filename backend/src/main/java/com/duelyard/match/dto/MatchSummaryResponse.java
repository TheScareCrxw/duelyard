package com.duelyard.match.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One finished match from a player's point of view.
 *
 * @param result       the player's result (WIN/LOSS/DRAW), or null if the match was abandoned
 * @param ratingChange rating delta from this match; null for casual or unrated matches
 */
public record MatchSummaryResponse(
        UUID matchId,
        String gameSlug,
        String gameName,
        String mode,
        String status,
        String endReason,
        String result,
        Integer score,
        Integer ratingBefore,
        Integer ratingAfter,
        Integer ratingChange,
        Instant endedAt,
        List<Opponent> opponents) {

    public record Opponent(String username, String avatarUrl, String result, Integer score) {
    }
}
