package com.duelyard.game.minigolf;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.duelyard.game.GameAvailability;
import com.duelyard.game.GameDefinition;
import com.duelyard.game.GameMode;
import com.duelyard.game.GameModule;

/**
 * Mini Golf game module. Rules and server-authoritative state will be added in its own phase,
 * after its rule set and state architecture have been agreed.
 */
@Component
public class MiniGolfModule implements GameModule {

    private static final GameDefinition DEFINITION = new GameDefinition(
        "mini-golf",
        "Mini Golf",
        "Bank shots, ramps, and windmills. Fewest strokes wins.",
        2,
        2,
        Set.of(GameMode.RANKED, GameMode.CASUAL),
        GameAvailability.COMING_SOON);

    @Override
    public GameDefinition definition() {
        return DEFINITION;
    }
}
