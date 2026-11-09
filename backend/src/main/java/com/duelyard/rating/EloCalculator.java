package com.duelyard.rating;

/**
 * Standard Elo for two players.
 *
 * <pre>
 *   E_A = 1 / (1 + 10^((R_B - R_A) / 400))
 *   R_A' = R_A + K * (S_A - E_A)          S: win = 1, draw = 0.5, loss = 0
 * </pre>
 *
 * Ratings are integers. The delta is computed once for player A and negated for player B, so a
 * match never creates or destroys rating points through rounding.
 */
public final class EloCalculator {

    public static final int K_FACTOR = 32;

    private EloCalculator() {
    }

    /** Probability-like expected score of A against B, in (0, 1). */
    public static double expectedScore(int ratingA, int ratingB) {
        return 1.0 / (1.0 + Math.pow(10.0, (ratingB - ratingA) / 400.0));
    }

    /**
     * Rating change for A (B's change is the negation).
     *
     * @param scoreA A's actual score: 1 win, 0.5 draw, 0 loss
     */
    public static int delta(int ratingA, int ratingB, double scoreA, int kFactor) {
        if (scoreA != 0.0 && scoreA != 0.5 && scoreA != 1.0) {
            throw new IllegalArgumentException("Score must be 0, 0.5 or 1: " + scoreA);
        }
        double raw = kFactor * (scoreA - expectedScore(ratingA, ratingB));
        // Round half away from zero so the result is symmetric for A and B.
        return (int) Math.signum(raw) * (int) Math.round(Math.abs(raw));
    }

    public static int delta(int ratingA, int ratingB, double scoreA) {
        return delta(ratingA, ratingB, scoreA, K_FACTOR);
    }
}
