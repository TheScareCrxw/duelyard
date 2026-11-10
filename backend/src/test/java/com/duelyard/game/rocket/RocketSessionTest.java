package com.duelyard.game.rocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.duelyard.game.engine.InvalidActionException;
import com.duelyard.match.MatchResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class RocketSessionTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static JsonNode fire(double angle, double power) {
        return JSON.valueToTree(Map.of("angle", angle, "power", power));
    }

    @Test
    void terrainStaysInBoundsAndLaunchersSitOnIt() {
        for (int seed = 0; seed < 50; seed++) {
            RocketSession s = new RocketSession(new Random(seed));
            RocketSession.View v = (RocketSession.View) s.view(0);
            for (double y : v.terrain()) assertThat(y).isBetween(140.0, RocketSession.HEIGHT - 30);
            for (double[] l : v.launchers()) assertThat(l[1]).isEqualTo(s.surfaceAt(l[0]));
            assertThat(Math.abs(v.wind())).isLessThanOrEqualTo(RocketSession.MAX_WIND);
        }
    }

    @Test
    void someShotHitsTheOpponentDamagingAndCarvingTerrain() {
        RocketSession probe = new RocketSession(new Random(2));
        probe.setWind(0);
        int[] best = null;
        // Search angles/powers on a copy of the same seeded game for a direct hit on seat 1.
        for (int angle = 20; angle <= 80 && best == null; angle++) {
            for (int p = 30; p <= 100 && best == null; p++) {
                RocketSession s = new RocketSession(new Random(2));
                s.setWind(0);
                RocketSession.ShotFired shot = (RocketSession.ShotFired) s.applyAction(0, fire(angle, p / 100.0)).getFirst();
                if (shot.damage()[1] >= 30) best = new int[] {angle, p};
            }
        }
        assertThat(best).as("a direct hit exists").isNotNull();

        RocketSession s = new RocketSession(new Random(2));
        s.setWind(0);
        double[] before = ((RocketSession.View) s.view(0)).terrain();
        RocketSession.ShotFired shot = (RocketSession.ShotFired) s.applyAction(0, fire(best[0], best[1] / 100.0)).getFirst();
        assertThat(shot.impact()).isNotNull();
        assertThat(shot.hp()[1]).isLessThanOrEqualTo(70);
        assertThat(shot.terrain()).isNotEqualTo(before);
        assertThat(s.turnSeat()).isEqualTo(1);
    }

    @Test
    void firingStraightUpCanHurtYourself() {
        RocketSession s = new RocketSession(new Random(4));
        s.setWind(0);
        RocketSession.ShotFired shot = (RocketSession.ShotFired) s.applyAction(0, fire(90, 0.2)).getFirst();
        assertThat(shot.damage()[0]).isPositive();
    }

    @Test
    void afterTwentyShotsHigherHpWins() {
        RocketSession s = new RocketSession(new Random(1));
        s.hp()[1] = 90;
        for (int i = 0; i < RocketSession.MAX_SHOTS; i++) s.onTurnTimeout(s.turnSeat());
        assertThat(s.outcome()).contains(List.of(MatchResult.WIN, MatchResult.LOSS));
        assertThatThrownBy(() -> s.onTurnTimeout(0)).isInstanceOf(InvalidActionException.class);
    }

    @Test
    void reducingOpponentToZeroWins() {
        RocketSession s = new RocketSession(new Random(1));
        s.setWind(0);
        s.hp()[1] = 1;
        s.launchers()[1] = s.launchers()[0].clone();   // stand in the blast zone of seat 0's own launcher
        s.launchers()[1][0] += 10;
        s.applyAction(0, fire(90, 0.2));
        assertThat(s.outcome()).isPresent();
    }

    @Test
    void validatesInput() {
        RocketSession s = new RocketSession(new Random(1));
        assertThatThrownBy(() -> s.applyAction(0, fire(200, 0.5))).isInstanceOf(InvalidActionException.class);
        assertThatThrownBy(() -> s.applyAction(1, fire(45, 0.5))).isInstanceOf(InvalidActionException.class);
    }
}
