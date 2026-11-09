package com.duelyard.game.minigolf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.duelyard.match.MatchResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class MiniGolfSessionTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static JsonNode shot(double angle, double power) {
        return JSON.valueToTree(Map.of("angle", angle, "power", power));
    }

    @Test
    void straightPuttUpTheFirstHoleDropsWithRightPaceAndLipsOutWhenTooFast() {
        Course hole = Course.HOLES.getFirst();
        // Tee (150,490), cup (150,80), bumper in between at (150,280): aim off-centre to avoid it.
        double tx = hole.tee()[0], ty = hole.tee()[1];
        boolean dropped = false;
        for (double power = 0.3; power <= 1.0 && !dropped; power += 0.01) {
            for (double a = -Math.PI / 2 - 0.25; a <= -Math.PI / 2 + 0.25 && !dropped; a += 0.005) {
                dropped = GolfPhysics.simulate(hole, tx, ty, a, power).holed();
            }
        }
        assertThat(dropped).as("some angle/power holes in one").isTrue();

        // Starting right next to the cup: gentle drops, full power rolls over.
        assertThat(GolfPhysics.simulate(hole, 150, 110, -Math.PI / 2, 0.2).holed()).isTrue();
        // Full power rolls straight over the cup (it may drop later, coming back off the wall).
        GolfPhysics.Result fast = GolfPhysics.simulate(hole, 150, 110, -Math.PI / 2, 1.0);
        assertThat(fast.path()).anySatisfy(p -> assertThat(p[1]).isLessThan(hole.cup()[1] - Course.CUP_RADIUS - 10));
    }

    @Test
    void ballNeverLeavesAnyCourse() {
        Random random = new Random(3);
        for (Course course : Course.HOLES) {
            for (int i = 0; i < 300; i++) {
                double angle = random.nextDouble() * 2 * Math.PI - Math.PI;
                double power = 0.05 + random.nextDouble() * 0.95;
                GolfPhysics.Result r = GolfPhysics.simulate(course, course.tee()[0], course.tee()[1], angle, power);
                assertThat(course.contains(r.x(), r.y())).as("%s angle=%s power=%s", course.name(), angle, power).isTrue();
                assertThat(r.path()).isNotEmpty();
            }
        }
    }

    @Test
    void playersAlternateAndPickUpAfterSixStrokes() {
        MiniGolfSession s = new MiniGolfSession();
        assertThat(s.turnSeat()).isZero();
        s.onTurnTimeout(0);
        assertThat(s.turnSeat()).isEqualTo(1);
        s.onTurnTimeout(1);
        // Both take 6 timed-out strokes: each records 7 and the hole ends.
        for (int i = 0; i < 5; i++) {
            s.onTurnTimeout(0);
            s.onTurnTimeout(1);
        }
        MiniGolfSession.View view = (MiniGolfSession.View) s.view(0);
        assertThat(view.hole()).isEqualTo(1);
        assertThat(view.strokes()[0][0]).isEqualTo(7);
        assertThat(view.strokes()[1][0]).isEqualTo(7);
        assertThat(view.turnSeat()).isEqualTo(1);   // seat order alternates per hole
    }

    @Test
    void fewestStrokesAfterThreeHolesWins() {
        // A single trivial course where the tee sits on the cup's doorstep: a soft putt always drops.
        Course easy = new Course("Test", 1, 200, 200, List.of(new double[] {0, 0}, new double[] {200, 0},
            new double[] {200, 200}, new double[] {0, 200}), List.of(), List.of(), new double[] {100, 130}, new double[] {100, 100});
        MiniGolfSession s = new MiniGolfSession(List.of(easy, easy, easy));
        JsonNode putt = shot(-Math.PI / 2, 0.2);
        for (int hole = 0; hole < 3; hole++) {
            int first = hole % 2;
            s.applyAction(first, putt);                          // first player holes in one
            s.onTurnTimeout(1 - first);                          // second player wastes a stroke
            s.applyAction(1 - first, putt);                      // then holes out: 2
        }
        // Who putts first alternates per hole: seat 0 takes 1+2+1, seat 1 takes 2+1+2.
        assertThat(((MiniGolfSession.View) s.view(0)).totals()).containsExactly(4, 5);
        assertThat(s.outcome()).contains(List.of(MatchResult.WIN, MatchResult.LOSS));
    }

    @Test
    void lowerTotalWins() {
        Course easy = new Course("Test", 1, 200, 200, List.of(new double[] {0, 0}, new double[] {200, 0},
            new double[] {200, 200}, new double[] {0, 200}), List.of(), List.of(), new double[] {100, 130}, new double[] {100, 100});
        MiniGolfSession s = new MiniGolfSession(List.of(easy));
        JsonNode putt = shot(-Math.PI / 2, 0.2);
        s.onTurnTimeout(0);
        s.applyAction(1, putt);
        s.applyAction(0, putt);
        assertThat(s.outcome()).contains(List.of(MatchResult.LOSS, MatchResult.WIN));
    }
}
