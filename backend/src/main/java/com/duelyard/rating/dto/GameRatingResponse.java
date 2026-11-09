package com.duelyard.rating.dto;

import com.duelyard.rating.PlayerGameStats;

/** A player's rating and record in one game. */
public record GameRatingResponse(
        String gameSlug,
        String gameName,
        int rating,
        int peakRating,
        int rankedWins,
        int rankedLosses,
        int rankedDraws,
        int rankedGamesPlayed,
        int casualWins,
        int casualLosses,
        int casualDraws) {

    public static GameRatingResponse from(PlayerGameStats s) {
        return new GameRatingResponse(
            s.getGame().getSlug(),
            s.getGame().getDisplayName(),
            s.getRating(),
            s.getPeakRating(),
            s.getRankedWins(),
            s.getRankedLosses(),
            s.getRankedDraws(),
            s.getRankedGamesPlayed(),
            s.getCasualWins(),
            s.getCasualLosses(),
            s.getCasualDraws());
    }
}
