package com.duelyard.match;

import java.util.List;
import java.util.UUID;

/**
 * Result of completing a match.
 *
 * @param alreadyCompleted true if the match had already been completed; nothing was changed
 * @param players          each player's result and rating change (rating fields null for casual)
 */
public record MatchCompletion(UUID matchId, boolean alreadyCompleted, List<PlayerChange> players) {

    public record PlayerChange(Long userId, MatchResult result, Integer ratingBefore, Integer ratingAfter) {
    }

    static MatchCompletion of(Match match, boolean alreadyCompleted) {
        return new MatchCompletion(match.getId(), alreadyCompleted, match.getPlayers().stream()
            .map(p -> new PlayerChange(p.getUser().getId(), p.getResult(), p.getRatingBefore(), p.getRatingAfter()))
            .toList());
    }
}
