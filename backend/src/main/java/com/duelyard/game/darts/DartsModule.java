package com.duelyard.game.darts;

import java.time.Duration;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

import com.duelyard.game.GameAvailability;
import com.duelyard.game.GameDefinition;
import com.duelyard.game.GameMode;
import com.duelyard.game.GameModule;
import com.duelyard.game.engine.GameSession;

/** Darts. Rules: docs/games.md. */
@Component
public class DartsModule implements GameModule {

    private static final GameDefinition DEFINITION = new GameDefinition(
        "darts",
        "Darts",
        "Steady your aim and race your rival down to exactly zero.",
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
        return new DartsSession(random);
    }

    @Override
    public Duration turnTimeLimit() {
        return Duration.ofSeconds(45);
    }
}
