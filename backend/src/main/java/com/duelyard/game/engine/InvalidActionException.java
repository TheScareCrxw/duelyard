package com.duelyard.game.engine;

/** A move the rules don't allow (wrong turn, malformed input, game over). Reported to the sender only. */
public class InvalidActionException extends RuntimeException {

    private final String code;

    public InvalidActionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static InvalidActionException notYourTurn() {
        return new InvalidActionException("NOT_YOUR_TURN", "It's not your turn.");
    }

    public static InvalidActionException gameOver() {
        return new InvalidActionException("GAME_OVER", "The game is over.");
    }

    public static InvalidActionException badInput(String detail) {
        return new InvalidActionException("INVALID_INPUT", detail);
    }
}
