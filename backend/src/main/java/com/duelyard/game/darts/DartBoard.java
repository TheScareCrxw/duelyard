package com.duelyard.game.darts;

/**
 * Standard dartboard geometry in millimetres, centre (0,0), x right, y up; 20 at the top.
 */
public final class DartBoard {

    public static final int[] SEGMENTS = {20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5};
    public static final double INNER_BULL = 6.35;
    public static final double OUTER_BULL = 15.9;
    public static final double TRIPLE_INNER = 99;
    public static final double TRIPLE_OUTER = 107;
    public static final double DOUBLE_INNER = 162;
    public static final double DOUBLE_OUTER = 170;

    private DartBoard() {
    }

    /** What a dart landing at (x, y) scores. */
    public record Hit(int points, String label) {
    }

    public static Hit score(double x, double y) {
        double r = Math.hypot(x, y);
        if (r <= INNER_BULL) return new Hit(50, "BULL");
        if (r <= OUTER_BULL) return new Hit(25, "25");
        if (r > DOUBLE_OUTER) return new Hit(0, "MISS");

        double angle = Math.toDegrees(Math.atan2(y, x));             // 0 = right, counter-clockwise
        double fromTop = ((90 - angle) % 360 + 360) % 360;           // clockwise from the top
        int segment = SEGMENTS[(int) (((fromTop + 9) % 360) / 18)];

        if (r >= TRIPLE_INNER && r <= TRIPLE_OUTER) return new Hit(segment * 3, "T" + segment);
        if (r >= DOUBLE_INNER) return new Hit(segment * 2, "D" + segment);
        return new Hit(segment, String.valueOf(segment));
    }
}
