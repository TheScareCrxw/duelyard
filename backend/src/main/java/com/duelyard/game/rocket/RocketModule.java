package com.duelyard.game.rocket;

import java.time.Duration;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

import com.duelyard.game.GameAvailability;
import com.duelyard.game.GameDefinition;
import com.duelyard.game.GameMode;
import com.duelyard.game.GameModule;
import com.duelyard.game.engine.GameSession;

/** Rocket Launcher. Rules: docs/games.md. */
@Component
public class RocketModule implements GameModule {

    private static final GameDefinition DEFINITION = new GameDefinition(
        "rocket-launcher",
        "Rocket Launcher",
        "Read the wind, arc your rockets and outgun your rival.",
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
        return new RocketSession(random);
    }

    @Override
    public Duration turnTimeLimit() {
        return Duration.ofSeconds(30);
    }
}
