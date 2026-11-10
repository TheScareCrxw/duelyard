package com.duelyard.game.darts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import com.duelyard.game.engine.InvalidActionException;
import com.duelyard.match.MatchResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class DartsSessionTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** A generator whose Gaussian is always 0: every dart lands exactly where aimed. */
    private static RandomGenerator perfectAim() {
        return new RandomGenerator() {
            @Override public long nextLong() { return 0; }
            @Override public double nextGaussian() { return 0; }
        };
    }

    private static JsonNode aim(double x, double y) {
        return JSON.valueToTree(java.util.Map.of("x", x, "y", y, "release", 0.5));
    }

    private static final JsonNode T20 = aim(0, 103);
    private static final JsonNode MISS = aim(0, 190);

    @Test
    void boardScoresSegmentsRingsAndBull() {
        assertThat(DartBoard.score(0, 0).points()).isEqualTo(50);
        assertThat(DartBoard.score(0, 10).points()).isEqualTo(25);
        assertThat(DartBoard.score(0, 103).label()).isEqualTo("T20");
        assertThat(DartBoard.score(0, 166).label()).isEqualTo("D20");
        assertThat(DartBoard.score(0, 140).label()).isEqualTo("20");
        assertThat(DartBoard.score(140, 0).label()).isEqualTo("6");      // right
        assertThat(DartBoard.score(0, -140).label()).isEqualTo("3");     // bottom
        assertThat(DartBoard.score(-140, 0).label()).isEqualTo("11");    // left
        // Just clockwise of 20 (top, slightly right) is 1.
        assertThat(DartBoard.score(30, 135).label()).isEqualTo("1");
        assertThat(DartBoard.score(0, 175).points()).isZero();
    }

    @Test
    void scatterGrowsAwayFromTheSweetSpot() {
        assertThat(DartsSession.sigmaFor(0.5)).isEqualTo(DartsSession.BASE_SIGMA);
        assertThat(DartsSession.sigmaFor(0.54)).isEqualTo(DartsSession.BASE_SIGMA);
        assertThat(DartsSession.sigmaFor(1.0)).isEqualTo(DartsSession.BASE_SIGMA + DartsSession.MAX_EXTRA_SIGMA);
        assertThat(DartsSession.sigmaFor(0.25)).isBetween(DartsSession.BASE_SIGMA, DartsSession.BASE_SIGMA + DartsSession.MAX_EXTRA_SIGMA);
    }

    @Test
    void threeDartsThenTurnPasses() {
        DartsSession s = new DartsSession(perfectAim());
        s.applyAction(0, T20);
        s.applyAction(0, T20);
        List<Object> events = s.applyAction(0, T20);

        assertThat(events).last().isInstanceOf(DartsSession.TurnEnded.class);
        assertThat(s.turnSeat()).isEqualTo(1);
        assertThat(((DartsSession.View) s.view(0)).scores()).containsExactly(121, 301);
        assertThatThrownBy(() -> s.applyAction(0, T20)).isInstanceOf(InvalidActionException.class);
    }

    @Test
    void bustRestoresTurnStartAndPassesTurn() {
        DartsSession s = new DartsSession(perfectAim());
        // Seat 0: 301 -> 121, seat 1 misses, seat 0: 121 -> 1 -> bust on next T20.
        for (int i = 0; i < 3; i++) s.applyAction(0, T20);
        for (int i = 0; i < 3; i++) s.applyAction(1, MISS);
        s.applyAction(0, T20);
        s.applyAction(0, T20);                      // 121 - 120 = 1
        List<Object> events = s.applyAction(0, T20); // would be -59: bust

        DartsSession.Throw bust = (DartsSession.Throw) events.getFirst();
        assertThat(bust.bust()).isTrue();
        assertThat(((DartsSession.View) s.view(0)).scores()[0]).isEqualTo(121);
        assertThat(s.turnSeat()).isEqualTo(1);
    }

    @Test
    void reachingExactlyZeroWins() {
        DartsSession s = new DartsSession(perfectAim());
        for (int i = 0; i < 3; i++) s.applyAction(0, T20);      // 121
        for (int i = 0; i < 3; i++) s.applyAction(1, MISS);
        s.applyAction(0, T20);                                   // 61
        s.applyAction(0, T20);                                   // 1
        assertThat(((DartsSession.View) s.view(0)).scores()[0]).isEqualTo(1);
        // Single 1 sits just clockwise of 20.
        List<Object> events = s.applyAction(0, aim(30, 135));
        assertThat(((DartsSession.Throw) events.getFirst()).finished()).isTrue();
        assertThat(s.outcome()).contains(List.of(MatchResult.WIN, MatchResult.LOSS));
        assertThat(s.turnSeat()).isEqualTo(-1);
        assertThatThrownBy(() -> s.applyAction(1, T20)).isInstanceOf(InvalidActionException.class);
    }

    @Test
    void bullThatOvershootsIsABustBackToTurnStart() {
        DartsSession s = new DartsSession(perfectAim());
        for (int i = 0; i < 3; i++) s.applyAction(0, T20);      // 121
        for (int i = 0; i < 3; i++) s.applyAction(1, MISS);
        s.applyAction(0, T20);                                   // 61
        s.applyAction(0, aim(0, 140));                           // 41
        DartsSession.Throw bust = (DartsSession.Throw) s.applyAction(0, aim(0, 0)).getFirst();   // 41 - 50
        assertThat(bust.bust()).isTrue();
        assertThat(bust.remaining()).isEqualTo(121);
    }

    @Test
    void afterTenRoundsLowerScoreWins() {
        DartsSession s = new DartsSession(perfectAim());
        for (int round = 0; round < DartsSession.MAX_ROUNDS; round++) {
            s.applyAction(0, round == 0 ? aim(0, 140) : MISS);   // seat 0 scores 20 once
            s.onTurnTimeout(0);
            s.onTurnTimeout(1);
        }
        assertThat(s.outcome()).contains(List.of(MatchResult.WIN, MatchResult.LOSS));
    }

    @Test
    void timeoutMissesRemainingDarts() {
        DartsSession s = new DartsSession(perfectAim());
        s.applyAction(0, T20);
        List<Object> events = s.onTurnTimeout(0);
        assertThat(events).hasSize(3);   // two misses + turn ended
        assertThat(((DartsSession.View) s.view(0)).scores()[0]).isEqualTo(241);
        assertThat(s.turnSeat()).isEqualTo(1);
    }

    @Test
    void rejectsMalformedOrOutOfRangeInput() {
        DartsSession s = new DartsSession(perfectAim());
        assertThatThrownBy(() -> s.applyAction(0, JSON.valueToTree(java.util.Map.of("x", 0, "y", 0))))
            .isInstanceOf(InvalidActionException.class);
        assertThatThrownBy(() -> s.applyAction(0, aim(0, 999))).isInstanceOf(InvalidActionException.class);
        assertThatThrownBy(() -> s.applyAction(0, JSON.readTree("{\"x\":\"T20\",\"y\":0,\"release\":0.5}")))
            .isInstanceOf(InvalidActionException.class);
        assertThatThrownBy(() -> s.applyAction(1, T20)).isInstanceOf(InvalidActionException.class);
    }
}
