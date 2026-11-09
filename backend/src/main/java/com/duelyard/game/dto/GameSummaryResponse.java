package com.duelyard.game.dto;

import java.util.List;

import com.duelyard.game.GameDefinition;
import com.duelyard.game.GameMode;

/** Public API representation of a game. Decoupled from {@link GameDefinition} so the API contract is explicit. */
public record GameSummaryResponse(
        String slug,
        String displayName,
        String tagline,
        int minPlayers,
        int maxPlayers,
        List<String> modes,
        String availability) {

    public static GameSummaryResponse from(GameDefinition d) {
        List<String> modes = d.modes().stream().sorted().map(GameMode::name).toList();
        return new GameSummaryResponse(
            d.slug(), d.displayName(), d.tagline(), d.minPlayers(), d.maxPlayers(), modes, d.availability().name());
    }
}
