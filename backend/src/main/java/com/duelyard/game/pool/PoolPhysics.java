package com.duelyard.game.pool;

import java.util.ArrayList;
import java.util.List;

/**
 * Pool table simulation (no spin). Playfield 1000 x 500 inside the cushions, y down. Fixed 1/600 s
 * steps; frames sampled at 30 fps. Equal-mass elastic ball collisions, reflective cushions, six
 * pockets that capture a ball whose centre comes within the pocket radius.
 */
final class PoolPhysics {

    static final double WIDTH = 1000;
    static final double HEIGHT = 500;
    static final double R = 14;
    static final double MAX_SPEED = 2400;
    static final double[][] POCKETS = {
        {0, 0, 25}, {500, -4, 22}, {1000, 0, 25},
        {0, 500, 25}, {500, 504, 22}, {1000, 500, 25},
    };
    private static final double ROLLING_DECEL = 260;
    private static final double DRAG = 0.25;
    private static final double BALL_RESTITUTION = 0.96;
    private static final double CUSHION_RESTITUTION = 0.75;
    private static final double STOP_SPEED = 6;
    private static final double DT = 1.0 / 600;
    private static final int FRAME_EVERY = 20;
    private static final double MAX_SECONDS = 14;

    /** Ball state. Index = ball number (0 = cue, 8 = eight ball). */
    static final class Ball {
        double x, y, vx, vy;
        boolean onTable;

        Ball(double x, double y) {
            this.x = x;
            this.y = y;
            this.onTable = true;
        }
    }

    /** What happened during one shot. {@code frames}: per frame, per ball, {x, y} or null once potted. */
    record ShotResult(Integer firstContact, List<Integer> potted, List<double[][]> frames) {
    }

    private PoolPhysics() {
    }

    static ShotResult shoot(Ball[] balls, double angle, double power) {
        Ball cue = balls[0];
        double speed = MAX_SPEED * power;
        cue.vx = Math.cos(angle) * speed;
        cue.vy = Math.sin(angle) * speed;

        Integer firstContact = null;
        List<Integer> potted = new ArrayList<>();
        List<double[][]> frames = new ArrayList<>();
        frames.add(snapshot(balls));

        int maxSteps = (int) (MAX_SECONDS / DT);
        for (int step = 1; step <= maxSteps; step++) {
            for (Ball b : balls) {
                if (!b.onTable) continue;
                b.x += b.vx * DT;
                b.y += b.vy * DT;
            }

            // Ball-ball collisions.
            for (int i = 0; i < balls.length; i++) {
                Ball a = balls[i];
                if (!a.onTable) continue;
                for (int j = i + 1; j < balls.length; j++) {
                    Ball b = balls[j];
                    if (!b.onTable) continue;
                    double dx = b.x - a.x, dy = b.y - a.y;
                    double dist = Math.hypot(dx, dy);
                    if (dist >= 2 * R || dist < 1e-9) continue;
                    double nx = dx / dist, ny = dy / dist;
                    double rel = (a.vx - b.vx) * nx + (a.vy - b.vy) * ny;
                    if (rel > 0) {
                        double impulse = (1 + BALL_RESTITUTION) / 2 * rel;
                        a.vx -= impulse * nx;
                        a.vy -= impulse * ny;
                        b.vx += impulse * nx;
                        b.vy += impulse * ny;
                        if (firstContact == null && (i == 0 || j == 0)) firstContact = i == 0 ? j : i;
                    }
                    double overlap = (2 * R - dist) / 2;
                    a.x -= nx * overlap;
                    a.y -= ny * overlap;
                    b.x += nx * overlap;
                    b.y += ny * overlap;
                }
            }

            boolean moving = false;
            for (int i = 0; i < balls.length; i++) {
                Ball b = balls[i];
                if (!b.onTable) continue;
                if (inPocket(b)) {
                    b.onTable = false;
                    b.vx = b.vy = 0;
                    potted.add(i);
                    continue;
                }
                if (b.x < R) { b.x = R; b.vx = Math.abs(b.vx) * CUSHION_RESTITUTION; }
                if (b.x > WIDTH - R) { b.x = WIDTH - R; b.vx = -Math.abs(b.vx) * CUSHION_RESTITUTION; }
                if (b.y < R) { b.y = R; b.vy = Math.abs(b.vy) * CUSHION_RESTITUTION; }
                if (b.y > HEIGHT - R) { b.y = HEIGHT - R; b.vy = -Math.abs(b.vy) * CUSHION_RESTITUTION; }

                double ballSpeed = Math.hypot(b.vx, b.vy);
                double next = Math.max(0, ballSpeed - (ROLLING_DECEL + DRAG * ballSpeed) * DT);
                if (next < STOP_SPEED) {
                    b.vx = b.vy = 0;
                } else {
                    b.vx *= next / ballSpeed;
                    b.vy *= next / ballSpeed;
                    moving = true;
                }
            }

            if (step % FRAME_EVERY == 0 || !moving) frames.add(snapshot(balls));
            if (!moving) break;
        }
        return new ShotResult(firstContact, potted, frames);
    }

    static boolean inPocket(Ball b) {
        for (double[] p : POCKETS) {
            if (Math.hypot(b.x - p[0], b.y - p[1]) < p[2]) return true;
        }
        return false;
    }

    static double[][] snapshot(Ball[] balls) {
        double[][] frame = new double[balls.length][];
        for (int i = 0; i < balls.length; i++) {
            Ball b = balls[i];
            frame[i] = b.onTable ? new double[] {Math.round(b.x * 10) / 10.0, Math.round(b.y * 10) / 10.0} : null;
        }
        return frame;
    }
}
