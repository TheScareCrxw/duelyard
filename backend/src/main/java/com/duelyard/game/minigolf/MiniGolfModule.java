package com.duelyard.game.minigolf;

import java.time.Duration;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

import com.duelyard.game.GameAvailability;
import com.duelyard.game.GameDefinition;
import com.duelyard.game.GameMode;
import com.duelyard.game.GameModule;
import com.duelyard.game.engine.GameSession;

/** Mini Golf. Rules: docs/games.md. */
@Component
public class MiniGolfModule implements GameModule {

    private static final GameDefinition DEFINITION = new GameDefinition(
        "mini-golf",
        "Mini Golf",
        "Bank shots, bumpers and doglegs. Fewest strokes wins.",
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
        return new MiniGolfSession();
    }

    @Override
    public Duration turnTimeLimit() {
        return Duration.ofSeconds(30);
    }
}
