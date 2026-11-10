package com.duelyard.rating;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.Random;

import org.junit.jupiter.api.Test;

class EloCalculatorTest {

    @Test
    void expectedScoreMatchesFormula() {
        assertThat(EloCalculator.expectedScore(1000, 1000)).isEqualTo(0.5);
        // 1 / (1 + 10^(200/400)) = 0.2402530734
        assertThat(EloCalculator.expectedScore(1000, 1200)).isCloseTo(0.2402530734, within(1e-9));
        assertThat(EloCalculator.expectedScore(1200, 1000) + EloCalculator.expectedScore(1000, 1200))
            .isCloseTo(1.0, within(1e-12));
    }

    @Test
    void evenMatchMovesSixteenPoints() {
        assertThat(EloCalculator.delta(1000, 1000, 1.0)).isEqualTo(16);
        assertThat(EloCalculator.delta(1000, 1000, 0.0)).isEqualTo(-16);
        assertThat(EloCalculator.delta(1000, 1000, 0.5)).isZero();
    }

    @Test
    void upsetsMoveMoreThanExpectedResults() {
        // Underdog wins: 32 * (1 - 0.2403) = 24.3 -> 24
        assertThat(EloCalculator.delta(1000, 1200, 1.0)).isEqualTo(24);
        // Favorite wins: 32 * (1 - 0.7597) = 7.7 -> 8
        assertThat(EloCalculator.delta(1200, 1000, 1.0)).isEqualTo(8);
        // Draw favors the underdog: 32 * (0.5 - 0.2403) = 8.3 -> 8
        assertThat(EloCalculator.delta(1000, 1200, 0.5)).isEqualTo(8);
    }

    @Test
    void roundingIsSymmetricSoMatchesAreZeroSum() {
        Random random = new Random(42);
        for (int i = 0; i < 10_000; i++) {
            int a = 600 + random.nextInt(1400);
            int b = 600 + random.nextInt(1400);
            double score = new double[] {0.0, 0.5, 1.0}[random.nextInt(3)];
            int deltaA = EloCalculator.delta(a, b, score);
            int deltaB = EloCalculator.delta(b, a, 1.0 - score);
            assertThat(deltaA + deltaB).as("a=%d b=%d s=%s", a, b, score).isZero();
            assertThat(Math.abs(deltaA)).isLessThanOrEqualTo(EloCalculator.K_FACTOR);
        }
    }

    @Test
    void rejectsInvalidScores() {
        assertThatThrownBy(() -> EloCalculator.delta(1000, 1000, 0.7)).isInstanceOf(IllegalArgumentException.class);
    }
}
