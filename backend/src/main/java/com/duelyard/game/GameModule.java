package com.duelyard.game;

import java.time.Duration;
import java.util.random.RandomGenerator;

import com.duelyard.game.engine.GameSession;

/**
 * Extension point for a mini-game. Each game lives in its own sub-package and contributes one
 * Spring bean implementing this interface; {@link GameRegistry} discovers it automatically, and
 * matchmaking, match rooms, ratings and leaderboards work with it without changes.
 */
public interface GameModule {

    GameDefinition definition();

    /** A fresh match. All randomness must come from {@code random}. */
    GameSession newSession(RandomGenerator random);

    /** Time a player has for one turn before the game's timeout move is applied. */
    Duration turnTimeLimit();
}
