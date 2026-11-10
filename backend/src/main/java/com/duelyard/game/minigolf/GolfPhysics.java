package com.duelyard.game.minigolf;

import java.util.ArrayList;
import java.util.List;

/** Deterministic ball simulation for one stroke. Fixed 1/240 s steps; frames sampled at 30 fps. */
final class GolfPhysics {

    static final double MAX_SPEED = 720;        // units/s at full power
    static final double ROLLING_DECEL = 150;    // units/s^2
    static final double DRAG = 0.45;            // per second, proportional to speed
    static final double WALL_RESTITUTION = 0.78;
    static final double BUMPER_RESTITUTION = 0.95;
    static final double MAX_CUP_SPEED = 260;    // faster than this rolls over the cup
    static final double STOP_SPEED = 5;
    private static final double DT = 1.0 / 240;
    private static final int FRAME_EVERY = 8;   // 240 / 8 = 30 fps
    private static final double MAX_SECONDS = 12;

    record Result(double x, double y, boolean holed, List<double[]> path) {
    }

    private GolfPhysics() {
    }

    static Result simulate(Course course, double startX, double startY, double angle, double power) {
        double x = startX;
        double y = startY;
        double speed = MAX_SPEED * power;
        double vx = Math.cos(angle) * speed;
        double vy = Math.sin(angle) * speed;
        double r = Course.BALL_RADIUS;
        List<double[]> walls = course.allWalls();
        List<double[]> path = new ArrayList<>();
        path.add(point(x, y));

        int maxSteps = (int) (MAX_SECONDS / DT);
        for (int step = 1; step <= maxSteps; step++) {
            x += vx * DT;
            y += vy * DT;

            for (double[] w : walls) {
                double[] hit = closestPoint(w, x, y);
                double dx = x - hit[0];
                double dy = y - hit[1];
                double dist = Math.hypot(dx, dy);
                if (dist < r && dist > 1e-9) {
                    double nx = dx / dist;
                    double ny = dy / dist;
                    double vn = vx * nx + vy * ny;
                    if (vn < 0) {
                        vx -= (1 + WALL_RESTITUTION) * vn * nx;
                        vy -= (1 + WALL_RESTITUTION) * vn * ny;
                    }
                    x = hit[0] + nx * r;
                    y = hit[1] + ny * r;
                }
            }
            for (double[] b : course.bumpers()) {
                double dx = x - b[0];
                double dy = y - b[1];
                double dist = Math.hypot(dx, dy);
                double min = b[2] + r;
                if (dist < min && dist > 1e-9) {
                    double nx = dx / dist;
                    double ny = dy / dist;
                    double vn = vx * nx + vy * ny;
                    if (vn < 0) {
                        vx -= (1 + BUMPER_RESTITUTION) * vn * nx;
                        vy -= (1 + BUMPER_RESTITUTION) * vn * ny;
                    }
                    x = b[0] + nx * min;
                    y = b[1] + ny * min;
                }
            }

            speed = Math.hypot(vx, vy);
            double toCup = Math.hypot(x - course.cup()[0], y - course.cup()[1]);
            if (toCup < Course.CUP_RADIUS && speed < MAX_CUP_SPEED) {
                path.add(point(course.cup()[0], course.cup()[1]));
                return new Result(course.cup()[0], course.cup()[1], true, path);
            }

            double newSpeed = Math.max(0, speed - (ROLLING_DECEL + DRAG * speed) * DT);
            if (newSpeed < STOP_SPEED) {
                break;
            }
            vx *= newSpeed / speed;
            vy *= newSpeed / speed;
            if (step % FRAME_EVERY == 0) path.add(point(x, y));
        }

        // Safety net: a ball that somehow left the course goes back to where it was struck.
        if (!course.contains(x, y)) {
            x = startX;
            y = startY;
        }
        path.add(point(x, y));
        return new Result(x, y, false, path);
    }

    private static double[] closestPoint(double[] w, double px, double py) {
        double ax = w[0], ay = w[1], bx = w[2], by = w[3];
        double abx = bx - ax, aby = by - ay;
        double len2 = abx * abx + aby * aby;
        double t = len2 == 0 ? 0 : Math.clamp(((px - ax) * abx + (py - ay) * aby) / len2, 0, 1);
        return new double[] {ax + t * abx, ay + t * aby};
    }

    private static double[] point(double x, double y) {
        return new double[] {Math.round(x * 10) / 10.0, Math.round(y * 10) / 10.0};
    }
}
