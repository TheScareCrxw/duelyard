package com.duelyard.game.pool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.duelyard.match.MatchResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PoolSessionTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static JsonNode shot(double angle, double power) {
        return JSON.valueToTree(Map.of("angle", angle, "power", power));
    }

    /** Angle from (x1,y1) to (x2,y2). */
    private static double aim(double x1, double y1, double x2, double y2) {
        return Math.atan2(y2 - y1, x2 - x1);
    }

    @Test
    void rackHasAllFifteenBallsWithoutOverlap() {
        PoolSession s = new PoolSession(new Random(1));
        for (int i = 0; i < 16; i++) {
            for (int j = i + 1; j < 16; j++) {
                double d = Math.hypot(s.ball(i).x - s.ball(j).x, s.ball(i).y - s.ball(j).y);
                assertThat(d).as("balls %d and %d", i, j).isGreaterThanOrEqualTo(2 * PoolPhysics.R - 1e-6);
            }
        }
    }

    @Test
    void breakSpreadsTheRackAndBallsStayOnTheTable() {
        Random random = new Random(7);
        for (int trial = 0; trial < 20; trial++) {
            PoolSession s = new PoolSession(new Random(trial));
            s.applyAction(0, shot((random.nextDouble() - 0.5) * 0.1, 1.0));
            for (int n = 0; n < 16; n++) {
                PoolPhysics.Ball b = s.ball(n);
                if (!b.onTable) continue;
                assertThat(b.x).isBetween(PoolPhysics.R - 0.01, PoolPhysics.WIDTH - PoolPhysics.R + 0.01);
                assertThat(b.y).isBetween(PoolPhysics.R - 0.01, PoolPhysics.HEIGHT - PoolPhysics.R + 0.01);
            }
        }
    }

    @Test
    void straightShotPotsBallInCornerAndShooterContinues() {
        PoolSession s = new PoolSession(new Random(1));
        s.arrange(Map.of(0, new double[] {700, 250}, 3, new double[] {850, 325}, 12, new double[] {300, 400}));
        s.assignGroups(PoolSession.Group.SOLIDS);
        // Cue -> 3 -> bottom-right corner pocket (1000,500): line up behind ball 3 on that line.
        double toPocket = aim(850, 325, 1000, 500);
        double ghostX = 850 - Math.cos(toPocket) * 2 * PoolPhysics.R;
        double ghostY = 325 - Math.sin(toPocket) * 2 * PoolPhysics.R;
        PoolSession.Shot result = (PoolSession.Shot) s.applyAction(0, shot(aim(700, 250, ghostX, ghostY), 0.5)).getFirst();

        assertThat(result.firstContact()).isEqualTo(3);
        assertThat(result.potted()).contains(3);
        assertThat(result.foul()).isFalse();
        assertThat(result.continues()).isTrue();
        assertThat(s.turnSeat()).isZero();
    }

    @Test
    void scratchIsAFoulThatPassesTurnAndRespotsCue() {
        PoolSession s = new PoolSession(new Random(1));
        s.arrange(Map.of(0, new double[] {900, 400}, 3, new double[] {300, 100}));
        s.assignGroups(PoolSession.Group.SOLIDS);
        PoolSession.Shot result = (PoolSession.Shot) s.applyAction(0, shot(aim(900, 400, 1000, 500), 0.6)).getFirst();

        assertThat(result.foul()).isTrue();
        assertThat(result.foulReason()).isEqualTo("SCRATCH");
        assertThat(s.turnSeat()).isEqualTo(1);
        assertThat(s.ball(0).onTable).isTrue();
        assertThat(s.ball(0).y).isEqualTo(PoolSession.MID_Y);
    }

    @Test
    void hittingOpponentsBallFirstIsAFoul() {
        PoolSession s = new PoolSession(new Random(1));
        s.arrange(Map.of(0, new double[] {300, 250}, 12, new double[] {500, 250}, 3, new double[] {700, 100}));
        s.assignGroups(PoolSession.Group.SOLIDS);
        PoolSession.Shot result = (PoolSession.Shot) s.applyAction(0, shot(0, 0.3)).getFirst();
        assertThat(result.firstContact()).isEqualTo(12);
        assertThat(result.foulReason()).isEqualTo("WRONG_BALL_FIRST");
        assertThat(s.turnSeat()).isEqualTo(1);
    }

    @Test
    void openTableAssignsGroupOfFirstPottedBall() {
        PoolSession s = new PoolSession(new Random(1));
        s.arrange(Map.of(0, new double[] {700, 250}, 11, new double[] {850, 325}, 2, new double[] {300, 100}));
        double toPocket = aim(850, 325, 1000, 500);
        double gx = 850 - Math.cos(toPocket) * 2 * PoolPhysics.R, gy = 325 - Math.sin(toPocket) * 2 * PoolPhysics.R;
        PoolSession.Shot result = (PoolSession.Shot) s.applyAction(1 - 1, shot(aim(700, 250, gx, gy), 0.5)).getFirst();
        assertThat(result.assignedGroup()).isEqualTo(PoolSession.Group.STRIPES);
        PoolSession.View view = (PoolSession.View) s.view(0);
        assertThat(view.groups()).containsExactly(PoolSession.Group.STRIPES, PoolSession.Group.SOLIDS);
    }

    @Test
    void pottingTheEightEarlyLosesAndAfterClearingWins() {
        double toPocket = aim(850, 325, 1000, 500);
        double gx = 850 - Math.cos(toPocket) * 2 * PoolPhysics.R, gy = 325 - Math.sin(toPocket) * 2 * PoolPhysics.R;

        PoolSession early = new PoolSession(new Random(1));
        early.arrange(Map.of(0, new double[] {700, 250}, 8, new double[] {850, 325}, 3, new double[] {300, 100}));
        early.assignGroups(PoolSession.Group.SOLIDS);
        early.applyAction(0, shot(aim(700, 250, gx, gy), 0.5));
        assertThat(early.outcome()).contains(List.of(MatchResult.LOSS, MatchResult.WIN));

        PoolSession cleared = new PoolSession(new Random(1));
        cleared.arrange(Map.of(0, new double[] {700, 250}, 8, new double[] {850, 325}, 12, new double[] {300, 100}));
        cleared.assignGroups(PoolSession.Group.SOLIDS);   // all solids already potted
        cleared.applyAction(0, shot(aim(700, 250, gx, gy), 0.5));
        assertThat(cleared.outcome()).contains(List.of(MatchResult.WIN, MatchResult.LOSS));
    }

    @Test
    void ballCountIsConservedAcrossRandomShots() {
        Random random = new Random(11);
        PoolSession s = new PoolSession(new Random(5));
        Set<Integer> potted = new HashSet<>();
        for (int i = 0; i < 40 && s.outcome().isEmpty(); i++) {
            int seat = s.turnSeat();
            PoolSession.Shot shot = (PoolSession.Shot) s.applyAction(seat,
                shot(random.nextDouble() * 2 * Math.PI - Math.PI, 0.2 + random.nextDouble() * 0.8)).getFirst();
            shot.potted().stream().filter(n -> n != 0 && n != 8).forEach(potted::add);
            long onTable = java.util.stream.IntStream.range(1, 16).filter(n -> n != 8 && s.ball(n).onTable).count();
            assertThat(onTable + potted.size()).isEqualTo(14);
            assertThat(s.ball(0).onTable || s.outcome().isPresent()).isTrue();
        }
    }
}
