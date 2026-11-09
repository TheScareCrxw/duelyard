package com.duelyard.support;

import java.time.Duration;
import java.util.Set;
import java.util.random.RandomGenerator;

import com.duelyard.game.GameAvailability;
import com.duelyard.game.GameDefinition;
import com.duelyard.game.GameMode;
import com.duelyard.game.GameModule;
import com.duelyard.game.engine.GameSession;

/** A GameModule with only a definition, for registry tests. */
public record StubGameModule(GameDefinition definition) implements GameModule {

    public static StubGameModule of(String slug, String name) {
        return new StubGameModule(new GameDefinition(slug, name, "", 2, 2, Set.of(GameMode.CASUAL), GameAvailability.AVAILABLE));
    }

    @Override
    public GameSession newSession(RandomGenerator random) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Duration turnTimeLimit() {
        return Duration.ofSeconds(30);
    }
}
