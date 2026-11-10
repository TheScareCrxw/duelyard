package com.duelyard.game.engine;

import java.util.List;
import java.util.Optional;

import com.duelyard.match.MatchResult;

import tools.jackson.databind.JsonNode;

/**
 * One match of one game, owned by the server. Implementations are plain, single-threaded objects
 * with no I/O: the room that owns a session serializes all calls to it. All randomness comes from
 * the generator the module was given when the session was created.
 *
 * <p>Clients only ever send inputs. The session validates them, simulates the result and reports
 * events; it is the sole authority on scores and outcomes.
 */
public interface GameSession {

    /**
     * Applies the move of the player in {@code seat}.
     *
     * @return events describing what happened (rendered/animated by clients)
     * @throws InvalidActionException if it isn't that seat's turn, the game is over, or the input is malformed
     */
    List<Object> applyAction(int seat, JsonNode input);

    /** The player in {@code seat} ran out of time: apply the game's timeout move. */
    List<Object> onTurnTimeout(int seat);

    /** Everything a client needs to render, from {@code viewerSeat}'s perspective. */
    Object view(int viewerSeat);

    /** Seat whose move it is; -1 when the game is over. */
    int turnSeat();

    /** Empty while playing; otherwise each seat's result, indexed by seat. */
    Optional<List<MatchResult>> outcome();
}
