package com.duelyard.rating.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A player's rating over time in one game, oldest first.
 *
 * @param initialRating the rating before the first point (1000 unless history was truncated)
 */
public record RatingHistoryResponse(String gameSlug, int initialRating, List<Point> points) {

    public record Point(UUID matchId, Instant at, int rating, int change) {
    }
}
