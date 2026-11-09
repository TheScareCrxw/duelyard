package com.duelyard.match;

/** A single player's outcome in a match. */
public enum MatchResult {
    WIN,
    LOSS,
    DRAW;

    /** Elo actual score: win = 1, draw = 0.5, loss = 0. */
    public double score() {
        return switch (this) {
            case WIN -> 1.0;
            case DRAW -> 0.5;
            case LOSS -> 0.0;
        };
    }
}
