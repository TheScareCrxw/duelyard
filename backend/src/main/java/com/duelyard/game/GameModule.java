package com.duelyard.game;

/**
 * Extension point for a mini-game. Each game lives in its own sub-package and contributes one
 * Spring bean implementing this interface; {@link GameRegistry} discovers them automatically.
 *
 * <p>Adding a new game requires no changes to shared modules. In later phases this interface will
 * grow the hooks the multiplayer layer needs (creating initial state, validating and applying a
 * player action, and deciding the authoritative outcome), keeping all game rules server-side.
 */
public interface GameModule {

    GameDefinition definition();
}
