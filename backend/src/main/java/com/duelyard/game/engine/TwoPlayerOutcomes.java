package com.duelyard.game.engine;

import java.util.List;

import com.duelyard.match.MatchResult;

/** Outcome lists for two-player games, indexed by seat. */
public final class TwoPlayerOutcomes {

    private TwoPlayerOutcomes() {
    }

    public static List<MatchResult> winner(int seat) {
        return seat == 0 ? List.of(MatchResult.WIN, MatchResult.LOSS) : List.of(MatchResult.LOSS, MatchResult.WIN);
    }

    public static List<MatchResult> draw() {
        return List.of(MatchResult.DRAW, MatchResult.DRAW);
    }

    /** Lower score wins (e.g. strokes, points remaining). */
    public static List<MatchResult> lowerWins(double seat0, double seat1) {
        return seat0 == seat1 ? draw() : winner(seat0 < seat1 ? 0 : 1);
    }

    /** Higher score wins (e.g. hit points). */
    public static List<MatchResult> higherWins(double seat0, double seat1) {
        return seat0 == seat1 ? draw() : winner(seat0 > seat1 ? 0 : 1);
    }
}
