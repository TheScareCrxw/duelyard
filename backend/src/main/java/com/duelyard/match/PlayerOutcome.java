package com.duelyard.match;

import java.util.Objects;

/** A player's server-determined outcome, supplied by a game module when its match ends. */
public record PlayerOutcome(Long userId, MatchResult result, Integer score) {

    public PlayerOutcome {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(result, "result");
    }

    public static PlayerOutcome win(Long userId) { return new PlayerOutcome(userId, MatchResult.WIN, null); }
    public static PlayerOutcome loss(Long userId) { return new PlayerOutcome(userId, MatchResult.LOSS, null); }
    public static PlayerOutcome draw(Long userId) { return new PlayerOutcome(userId, MatchResult.DRAW, null); }
}
