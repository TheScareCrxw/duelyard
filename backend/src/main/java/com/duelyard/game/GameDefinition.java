package com.duelyard.game;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Static metadata describing a game. Shared services (matchmaking, rating, leaderboards) only ever
 * reference games through their {@code slug}, never through game-specific classes.
 *
 * @param slug        stable, URL-safe identifier (e.g. {@code "darts"}); also the persistence key
 * @param displayName human-readable name
 * @param tagline     one-line description for game cards
 * @param minPlayers  minimum players per match
 * @param maxPlayers  maximum players per match
 * @param modes       queue modes this game supports
 * @param availability whether the game is playable yet
 */
public record GameDefinition(
        String slug,
        String displayName,
        String tagline,
        int minPlayers,
        int maxPlayers,
        Set<GameMode> modes,
        GameAvailability availability) {

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    public GameDefinition {
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(availability, "availability");
        if (!SLUG.matcher(slug).matches()) {
            throw new IllegalArgumentException("Invalid game slug: " + slug);
        }
        if (minPlayers < 1 || maxPlayers < minPlayers) {
            throw new IllegalArgumentException("Invalid player bounds for " + slug);
        }
        modes = Set.copyOf(modes);
        if (modes.isEmpty()) {
            throw new IllegalArgumentException("Game " + slug + " must support at least one mode");
        }
    }
}
