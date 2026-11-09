package com.duelyard.game.pool;

import java.time.Duration;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

import com.duelyard.game.GameAvailability;
import com.duelyard.game.GameDefinition;
import com.duelyard.game.GameMode;
import com.duelyard.game.GameModule;
import com.duelyard.game.engine.GameSession;

/** 8-Ball Pool. Rules: docs/games.md. */
@Component
public class PoolModule implements GameModule {

    private static final GameDefinition DEFINITION = new GameDefinition(
        "eight-ball",
        "8-Ball Pool",
        "Clear your group, then sink the eight.",
        2,
        2,
        Set.of(GameMode.RANKED, GameMode.CASUAL),
        GameAvailability.AVAILABLE);

    @Override
    public GameDefinition definition() {
        return DEFINITION;
    }

    @Override
    public GameSession newSession(RandomGenerator random) {
        return new PoolSession(random);
    }

    @Override
    public Duration turnTimeLimit() {
        return Duration.ofSeconds(45);
    }
}
