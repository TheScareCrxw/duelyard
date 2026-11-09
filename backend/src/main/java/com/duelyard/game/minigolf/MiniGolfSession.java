package com.duelyard.game.minigolf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.duelyard.game.engine.GameSession;
import com.duelyard.game.engine.Inputs;
import com.duelyard.game.engine.InvalidActionException;
import com.duelyard.game.engine.TwoPlayerOutcomes;
import com.duelyard.match.MatchResult;

import tools.jackson.databind.JsonNode;

/** Three holes, alternating strokes, max 6 per hole (else 7), fewest total wins. See docs/games.md. */
public class MiniGolfSession implements GameSession {

    public static final int MAX_STROKES = 6;
    public static final int PICKUP_SCORE = 7;

    public record Stroke(int seat, int hole, List<double[]> path, boolean holed, int strokes, boolean timedOut) {
    }

    public record HoleFinished(int hole, int[] strokes) {
    }

    public record View(int hole, int holeCount, Course course, double[][] balls, boolean[] done, int[][] strokes,
                       int[] totals, int turnSeat) {
    }

    private final List<Course> holes;
    private final int[][] strokes;
    private int hole = 0;
    private double[][] balls;
    private boolean[] done;
    private int turnSeat;
    private List<MatchResult> outcome;

    public MiniGolfSession() {
        this(Course.HOLES);
    }

    MiniGolfSession(List<Course> holes) {
        this.holes = holes;
        this.strokes = new int[2][holes.size()];
        startHole();
    }

    private void startHole() {
        double[] tee = holes.get(hole).tee();
        balls = new double[][] {tee.clone(), tee.clone()};
        done = new boolean[] {false, false};
        turnSeat = hole % 2;
    }

    @Override
    public List<Object> applyAction(int seat, JsonNode input) {
        requireTurn(seat);
        double angle = Inputs.number(input, "angle", -Math.PI, Math.PI);
        double power = Inputs.number(input, "power", 0.02, 1);

        Course course = holes.get(hole);
        GolfPhysics.Result result = GolfPhysics.simulate(course, balls[seat][0], balls[seat][1], angle, power);
        balls[seat] = new double[] {result.x(), result.y()};
        return afterStroke(seat, new Stroke(seat, hole, result.path(), result.holed(), ++strokes[seat][hole], false),
            result.holed());
    }

    @Override
    public List<Object> onTurnTimeout(int seat) {
        requireTurn(seat);
        double[] at = balls[seat];
        return afterStroke(seat, new Stroke(seat, hole, List.of(at.clone()), false, ++strokes[seat][hole], true), false);
    }

    private List<Object> afterStroke(int seat, Stroke stroke, boolean holed) {
        List<Object> events = new ArrayList<>();
        events.add(stroke);
        if (holed) {
            done[seat] = true;
        } else if (strokes[seat][hole] >= MAX_STROKES) {
            strokes[seat][hole] = PICKUP_SCORE;
            done[seat] = true;
        }

        if (done[0] && done[1]) {
            events.add(new HoleFinished(hole, new int[] {strokes[0][hole], strokes[1][hole]}));
            hole++;
            if (hole == holes.size()) {
                int[] totals = totals();
                outcome = TwoPlayerOutcomes.lowerWins(totals[0], totals[1]);
                hole = holes.size() - 1;
            } else {
                startHole();
            }
        } else {
            int other = 1 - seat;
            turnSeat = done[other] ? seat : other;
        }
        return events;
    }

    private int[] totals() {
        return new int[] {Arrays.stream(strokes[0]).sum(), Arrays.stream(strokes[1]).sum()};
    }

    private void requireTurn(int seat) {
        if (outcome != null) throw InvalidActionException.gameOver();
        if (seat != turnSeat) throw InvalidActionException.notYourTurn();
    }

    @Override
    public Object view(int viewerSeat) {
        return new View(hole, holes.size(), holes.get(hole), new double[][] {balls[0].clone(), balls[1].clone()},
            done.clone(), new int[][] {strokes[0].clone(), strokes[1].clone()}, totals(), turnSeat());
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
