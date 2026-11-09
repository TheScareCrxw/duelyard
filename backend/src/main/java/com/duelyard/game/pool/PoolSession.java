package com.duelyard.game.pool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

import com.duelyard.game.engine.GameSession;
import com.duelyard.game.engine.Inputs;
import com.duelyard.game.engine.InvalidActionException;
import com.duelyard.game.engine.TwoPlayerOutcomes;
import com.duelyard.match.MatchResult;

import tools.jackson.databind.JsonNode;

/** 8-ball with simplified rules (no spin, no called pockets, cue re-spotted after a scratch). See docs/games.md. */
public class PoolSession implements GameSession {

    public enum Group { SOLIDS, STRIPES }

    static final double HEAD_X = 250;
    static final double FOOT_X = 750;
    static final double MID_Y = 250;

    /** One shot. {@code frames}: per frame, per ball number, {x, y} or null if potted. */
    public record Shot(int seat, List<double[][]> frames, Integer firstContact, List<Integer> potted, boolean foul,
                       String foulReason, boolean continues, Group assignedGroup) {
    }

    public record View(double[][] balls, Group[] groups, boolean openTable, boolean breakShot, int turnSeat,
                       int[] remaining) {
    }

    private final PoolPhysics.Ball[] balls = new PoolPhysics.Ball[16];
    private final Group[] groups = new Group[2];
    private boolean breakShot = true;
    private int turnSeat = 0;
    private List<MatchResult> outcome;

    public PoolSession(RandomGenerator random) {
        balls[0] = new PoolPhysics.Ball(HEAD_X, MID_Y);
        rack(random);
    }

    /** Triangle at the foot spot: 8 in the centre, a solid and a stripe in the back corners. */
    private void rack(RandomGenerator random) {
        List<Integer> others = new ArrayList<>(IntStream.rangeClosed(1, 15).boxed().filter(n -> n != 8 && n != 1 && n != 9).toList());
        Collections.shuffle(others, new java.util.Random(random.nextLong()));
        double dx = PoolPhysics.R * Math.sqrt(3) + 0.5;
        double dy = PoolPhysics.R + 0.25;
        int[][] slots = new int[15][2];
        int k = 0;
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col <= row; col++) slots[k++] = new int[] {row, col};
        }
        int othersIndex = 0;
        for (int s = 0; s < 15; s++) {
            int row = slots[s][0], col = slots[s][1];
            int number;
            if (s == 4) number = 8;                       // centre of the third row
            else if (s == 10) number = 1;                  // back row, one corner
            else if (s == 14) number = 9;                  // back row, other corner
            else number = others.get(othersIndex++);
            double x = FOOT_X + row * dx;
            double y = MID_Y + (col - row / 2.0) * 2 * dy;
            balls[number] = new PoolPhysics.Ball(x, y);
        }
    }

    @Override
    public List<Object> applyAction(int seat, JsonNode input) {
        requireTurn(seat);
        double angle = Inputs.number(input, "angle", -Math.PI, Math.PI);
        double power = Inputs.number(input, "power", 0.05, 1);

        boolean wasBreak = breakShot;
        breakShot = false;
        boolean groupClearedBefore = groups[seat] != null && remaining(groups[seat]) == 0;
        PoolPhysics.ShotResult result = PoolPhysics.shoot(balls, angle, power);

        boolean cuePotted = result.potted().contains(0);
        boolean eightPotted = result.potted().contains(8);
        String foul = foulReason(seat, result.firstContact(), cuePotted, wasBreak, groupClearedBefore);

        if (eightPotted && wasBreak) {
            // Potting the 8 on the break neither wins nor loses: it goes back on the foot spot.
            respot(8, FOOT_X, MID_Y);
        } else if (eightPotted) {
            outcome = TwoPlayerOutcomes.winner(foul == null && groupClearedBefore ? seat : 1 - seat);
            return List.of(new Shot(seat, result.frames(), result.firstContact(), result.potted(), foul != null, foul, false, null));
        }
        if (cuePotted) respot(0, HEAD_X, MID_Y);

        Group assigned = null;
        boolean continues = false;
        if (foul == null) {
            List<Integer> objectPotted = result.potted().stream().filter(n -> n != 0 && n != 8).toList();
            if (groups[seat] == null && !wasBreak && !objectPotted.isEmpty()) {
                assigned = groupOf(objectPotted.getFirst());
                groups[seat] = assigned;
                groups[1 - seat] = assigned == Group.SOLIDS ? Group.STRIPES : Group.SOLIDS;
            }
            Group mine = groups[seat];
            continues = wasBreak ? !objectPotted.isEmpty() : mine != null && objectPotted.stream().anyMatch(n -> groupOf(n) == mine);
        }
        if (!continues) turnSeat = 1 - seat;

        return List.of(new Shot(seat, result.frames(), result.firstContact(), result.potted(), foul != null, foul,
            continues, assigned));
    }

    private String foulReason(int seat, Integer firstContact, boolean cuePotted, boolean wasBreak, boolean groupCleared) {
        if (cuePotted) return "SCRATCH";
        if (firstContact == null) return "NO_CONTACT";
        if (wasBreak) return null;
        Group mine = groups[seat];
        if (mine == null) return firstContact == 8 ? "WRONG_BALL_FIRST" : null;
        boolean legalFirst = groupCleared ? firstContact == 8 : groupOf(firstContact) == mine;
        return legalFirst ? null : "WRONG_BALL_FIRST";
    }

    @Override
    public List<Object> onTurnTimeout(int seat) {
        requireTurn(seat);
        breakShot = false;
        turnSeat = 1 - seat;
        return List.of(new Shot(seat, List.<double[][]>of(PoolPhysics.snapshot(balls)), null, List.of(), true, "TIMEOUT", false, null));
    }

    /** Puts a ball back at (x, y), sliding left along the line until it doesn't overlap another ball. */
    private void respot(int number, double x, double y) {
        PoolPhysics.Ball b = balls[number];
        b.onTable = true;
        b.vx = b.vy = 0;
        b.y = y;
        for (double cx = x; cx > PoolPhysics.R; cx -= 2) {
            b.x = cx;
            if (!overlapsAny(number)) return;
        }
    }

    private boolean overlapsAny(int number) {
        PoolPhysics.Ball b = balls[number];
        for (int i = 0; i < balls.length; i++) {
            if (i == number || !balls[i].onTable) continue;
            if (Math.hypot(balls[i].x - b.x, balls[i].y - b.y) < 2 * PoolPhysics.R) return true;
        }
        return false;
    }

    static Group groupOf(int number) {
        return number < 8 ? Group.SOLIDS : Group.STRIPES;
    }

    private int remaining(Group group) {
        int count = 0;
        for (int n = 1; n <= 15; n++) {
            if (n != 8 && groupOf(n) == group && balls[n].onTable) count++;
        }
        return count;
    }

    private void requireTurn(int seat) {
        if (outcome != null) throw InvalidActionException.gameOver();
        if (seat != turnSeat) throw InvalidActionException.notYourTurn();
    }

    // --- test hooks (package-private) ----------------------------------------------------------

    PoolPhysics.Ball ball(int number) {
        return balls[number];
    }

    /** Places only the given balls on the table (all others potted), with the break already taken. */
    void arrange(java.util.Map<Integer, double[]> positions) {
        for (int n = 0; n < balls.length; n++) {
            double[] p = positions.get(n);
            balls[n].onTable = p != null;
            balls[n].vx = balls[n].vy = 0;
            if (p != null) {
                balls[n].x = p[0];
                balls[n].y = p[1];
            }
        }
        breakShot = false;
    }

    void assignGroups(Group seat0) {
        groups[0] = seat0;
        groups[1] = seat0 == Group.SOLIDS ? Group.STRIPES : Group.SOLIDS;
    }

    void setTurn(int seat) {
        turnSeat = seat;
    }

    @Override
    public Object view(int viewerSeat) {
        int[] remaining = {
            groups[0] == null ? 7 : remaining(groups[0]),
            groups[1] == null ? 7 : remaining(groups[1]),
        };
        return new View(PoolPhysics.snapshot(balls), groups.clone(), groups[0] == null, breakShot, turnSeat(), remaining);
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
