package com.duelyard.game.rocket;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

import com.duelyard.game.engine.GameSession;
import com.duelyard.game.engine.Inputs;
import com.duelyard.game.engine.InvalidActionException;
import com.duelyard.game.engine.TwoPlayerOutcomes;
import com.duelyard.match.MatchResult;

import tools.jackson.databind.JsonNode;

/**
 * Artillery duel on destructible terrain. World 1000 x 500, y down. Terrain is a heightmap of
 * surface y values every {@link #STEP} units. See docs/games.md.
 */
public class RocketSession implements GameSession {

    public static final double WIDTH = 1000;
    public static final double HEIGHT = 500;
    public static final int STEP = 5;
    public static final int MAX_HP = 100;
    public static final int MAX_SHOTS = 20;
    static final double GRAVITY = 380;
    static final double MIN_SPEED = 160;
    static final double MAX_SPEED = 820;
    static final double MAX_WIND = 110;
    static final double BLAST_RADIUS = 60;
    static final double MAX_DAMAGE = 40;
    static final double HIT_RADIUS = 18;
    static final double[] LAUNCHER_X = {120, 880};
    private static final double DT = 1.0 / 240;
    private static final int FRAME_EVERY = 8;
    private static final double MAX_SECONDS = 12;

    public record ShotFired(int seat, double angle, double power, double wind, List<double[]> path, double[] impact,
                            int[] damage, int[] hp, double[] terrain, double[][] launchers) {
    }

    public record TurnSkipped(int seat) {
    }

    public record View(double[] terrain, double[][] launchers, int[] hp, double wind, int shotsFired, int maxShots,
                       int turnSeat) {
    }

    private final RandomGenerator random;
    private final double[] terrain;
    private final double[][] launchers = new double[2][2];
    private final int[] hp = {MAX_HP, MAX_HP};
    private double wind;
    private int shotsFired = 0;
    private int turnSeat = 0;
    private List<MatchResult> outcome;

    public RocketSession(RandomGenerator random) {
        this.random = random;
        this.terrain = generateTerrain(random);
        for (int seat = 0; seat < 2; seat++) {
            launchers[seat] = new double[] {LAUNCHER_X[seat], surfaceAt(LAUNCHER_X[seat])};
        }
        rollWind();
    }

    /** Rolling hills from a few random sine waves, flattened under each launcher. */
    static double[] generateTerrain(RandomGenerator random) {
        int n = (int) (WIDTH / STEP) + 1;
        double[] t = new double[n];
        double p1 = random.nextDouble() * Math.PI * 2, p2 = random.nextDouble() * Math.PI * 2, p3 = random.nextDouble() * Math.PI * 2;
        double a1 = 40 + random.nextDouble() * 30, a2 = 20 + random.nextDouble() * 20, a3 = 6 + random.nextDouble() * 6;
        for (int i = 0; i < n; i++) {
            double x = i * STEP;
            double hill = Math.sin(x / 1000 * Math.PI) * 70;   // a ridge in the middle
            t[i] = 340 - hill
                - a1 * Math.sin(x / 160 + p1)
                - a2 * Math.sin(x / 70 + p2)
                - a3 * Math.sin(x / 23 + p3);
        }
        for (double lx : LAUNCHER_X) {
            int centre = (int) (lx / STEP);
            double flat = t[centre];
            for (int i = Math.max(0, centre - 5); i <= Math.min(n - 1, centre + 5); i++) t[i] = flat;
        }
        for (int i = 0; i < n; i++) t[i] = Math.clamp(t[i], 140, HEIGHT - 30);
        return t;
    }

    double surfaceAt(double x) {
        double pos = Math.clamp(x, 0, WIDTH) / STEP;
        int i = (int) Math.floor(pos);
        if (i >= terrain.length - 1) return terrain[terrain.length - 1];
        double f = pos - i;
        return terrain[i] * (1 - f) + terrain[i + 1] * f;
    }

    private void rollWind() {
        wind = Math.round((random.nextDouble() * 2 - 1) * MAX_WIND);
    }

    @Override
    public List<Object> applyAction(int seat, JsonNode input) {
        requireTurn(seat);
        double angleDeg = Inputs.number(input, "angle", 0, 180);
        double power = Inputs.number(input, "power", 0, 1);

        double angle = Math.toRadians(angleDeg);
        double speed = MIN_SPEED + (MAX_SPEED - MIN_SPEED) * power;
        double x = launchers[seat][0];
        double y = launchers[seat][1] - 14;   // muzzle above the launcher
        double vx = Math.cos(angle) * speed;
        double vy = -Math.sin(angle) * speed;

        List<double[]> path = new ArrayList<>();
        path.add(point(x, y));
        double[] impact = null;
        int maxSteps = (int) (MAX_SECONDS / DT);
        for (int step = 1; step <= maxSteps; step++) {
            vx += wind * DT;
            vy += GRAVITY * DT;
            x += vx * DT;
            y += vy * DT;
            if (x < -60 || x > WIDTH + 60 || y > HEIGHT + 60) break;   // flew off the map
            boolean hitsLauncher = step > 12 && (Math.hypot(x - launchers[0][0], y - launchers[0][1] + 8) < HIT_RADIUS
                || Math.hypot(x - launchers[1][0], y - launchers[1][1] + 8) < HIT_RADIUS);
            if ((x >= 0 && x <= WIDTH && y >= surfaceAt(x)) || hitsLauncher) {
                impact = point(x, Math.min(y, HEIGHT));
                break;
            }
            if (step % FRAME_EVERY == 0) path.add(point(x, y));
        }
        if (impact != null) path.add(impact);

        int[] damage = {0, 0};
        if (impact != null) {
            for (int s = 0; s < 2; s++) {
                double d = Math.hypot(launchers[s][0] - impact[0], launchers[s][1] - 8 - impact[1]);
                if (d < BLAST_RADIUS) damage[s] = (int) Math.round(MAX_DAMAGE * (1 - d / BLAST_RADIUS));
                hp[s] = Math.max(0, hp[s] - damage[s]);
            }
            carveCrater(impact[0], impact[1]);
            for (int s = 0; s < 2; s++) launchers[s][1] = surfaceAt(launchers[s][0]);
        }

        shotsFired++;
        double windUsed = wind;
        finishTurn(seat);
        return List.of(new ShotFired(seat, angleDeg, power, windUsed, path, impact, damage, hp.clone(), terrain.clone(),
            new double[][] {launchers[0].clone(), launchers[1].clone()}));
    }

    @Override
    public List<Object> onTurnTimeout(int seat) {
        requireTurn(seat);
        shotsFired++;
        finishTurn(seat);
        return List.of(new TurnSkipped(seat));
    }

    private void finishTurn(int seat) {
        if (hp[0] == 0 || hp[1] == 0) {
            outcome = hp[0] == hp[1] ? TwoPlayerOutcomes.draw() : TwoPlayerOutcomes.winner(hp[0] == 0 ? 1 : 0);
        } else if (shotsFired >= MAX_SHOTS) {
            outcome = TwoPlayerOutcomes.higherWins(hp[0], hp[1]);
        } else {
            turnSeat = 1 - seat;
            rollWind();
        }
    }

    private void carveCrater(double cx, double cy) {
        for (int i = 0; i < terrain.length; i++) {
            double dx = i * STEP - cx;
            if (Math.abs(dx) >= BLAST_RADIUS * 0.7) continue;
            double r = BLAST_RADIUS * 0.7;
            double bottom = cy + Math.sqrt(r * r - dx * dx);
            if (bottom > terrain[i]) terrain[i] = Math.min(HEIGHT - 5, bottom);
        }
    }

    private void requireTurn(int seat) {
        if (outcome != null) throw InvalidActionException.gameOver();
        if (seat != turnSeat) throw InvalidActionException.notYourTurn();
    }

    private static double[] point(double x, double y) {
        return new double[] {Math.round(x * 10) / 10.0, Math.round(y * 10) / 10.0};
    }

    /** Test hooks. */
    int[] hp() { return hp; }
    double[][] launchers() { return launchers; }
    void setWind(double wind) { this.wind = wind; }

    @Override
    public Object view(int viewerSeat) {
        return new View(terrain.clone(), new double[][] {launchers[0].clone(), launchers[1].clone()}, hp.clone(), wind,
            shotsFired, MAX_SHOTS, turnSeat());
    }

    @Override
    public int turnSeat() {
        return outcome == null ? turnSeat : -1;
    }

    @Override
    public Optional<List<MatchResult>> outcome() {
        return Optional.ofNullable(outcome);
    }
}
