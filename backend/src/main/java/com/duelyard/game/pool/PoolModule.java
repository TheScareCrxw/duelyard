package com.duelyard.game.pool;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.duelyard.game.GameAvailability;
import com.duelyard.game.GameDefinition;
import com.duelyard.game.GameMode;
import com.duelyard.game.GameModule;

/**
 * 8-Ball Pool game module. Rules and server-authoritative state will be added in its own phase,
 * after its rule set and state architecture have been agreed.
 */
@Component
public class PoolModule implements GameModule {

    private static final GameDefinition DEFINITION = new GameDefinition(
        "eight-ball",
        "8-Ball Pool",
        "Call your shots, clear your group, sink the eight.",
        2,
        2,
        Set.of(GameMode.RANKED, GameMode.CASUAL),
        GameAvailability.COMING_SOON);

    @Override
    public GameDefinition definition() {
        return DEFINITION;
    }
}
