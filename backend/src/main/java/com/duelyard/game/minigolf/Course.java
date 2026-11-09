package com.duelyard.game.minigolf;

import java.util.ArrayList;
import java.util.List;

/**
 * A mini golf hole in screen coordinates (x right, y down). Walls are line segments; the outer
 * boundary is given as a closed polygon and becomes walls too.
 */
public record Course(String name, int par, double width, double height, List<double[]> boundary,
                     List<double[]> walls, List<double[]> bumpers, double[] tee, double[] cup) {

    public static final double BALL_RADIUS = 6;
    public static final double CUP_RADIUS = 10;

    /** Every wall segment as {x1, y1, x2, y2}: boundary edges plus interior walls. */
    public List<double[]> allWalls() {
        List<double[]> segments = new ArrayList<>(walls);
        for (int i = 0; i < boundary.size(); i++) {
            double[] a = boundary.get(i);
            double[] b = boundary.get((i + 1) % boundary.size());
            segments.add(new double[] {a[0], a[1], b[0], b[1]});
        }
        return segments;
    }

    /** True if (x, y) lies inside the boundary polygon (ray casting). */
    public boolean contains(double x, double y) {
        boolean inside = false;
        for (int i = 0, j = boundary.size() - 1; i < boundary.size(); j = i++) {
            double[] a = boundary.get(i);
            double[] b = boundary.get(j);
            if ((a[1] > y) != (b[1] > y) && x < (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]) + a[0]) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static double[] p(double x, double y) {
        return new double[] {x, y};
    }

    /** The three holes every match plays. Original layouts. */
    public static final List<Course> HOLES = List.of(
        new Course("Warm-Up Lane", 2, 300, 560,
            List.of(p(20, 20), p(280, 20), p(280, 540), p(20, 540)),
            List.of(),
            List.of(new double[] {150, 280, 26}),
            p(150, 490), p(150, 80)),
        new Course("Dogleg Drift", 3, 400, 560,
            List.of(p(20, 20), p(380, 20), p(380, 230), p(170, 230), p(170, 540), p(20, 540)),
            List.of(new double[] {170, 230, 120, 280}),
            List.of(new double[] {300, 110, 22}),
            p(95, 490), p(330, 85)),
        new Course("Bank Vault", 3, 360, 560,
            List.of(p(20, 20), p(340, 20), p(340, 540), p(20, 540)),
            List.of(new double[] {20, 300, 250, 300}, new double[] {110, 190, 340, 190}),
            List.of(new double[] {290, 420, 18}, new double[] {70, 110, 18}),
            p(60, 490), p(300, 70))
    );
}
