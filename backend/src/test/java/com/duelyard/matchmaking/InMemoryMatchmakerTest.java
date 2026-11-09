package com.duelyard.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.duelyard.game.GameMode;

class InMemoryMatchmakerTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private static Matchmaker.Entry ranked(long user, int rating, int secondsAgo) {
        return new Matchmaker.Entry(user, "darts", GameMode.RANKED, rating, T0.minusSeconds(secondsAgo));
    }

    @Test
    void rangeStartsAt100AndWidensEveryFiveSecondsUpTo600() {
        assertThat(InMemoryMatchmaker.ratingRange(ranked(1, 1000, 0), T0)).isEqualTo(100);
        assertThat(InMemoryMatchmaker.ratingRange(ranked(1, 1000, 4), T0)).isEqualTo(100);
        assertThat(InMemoryMatchmaker.ratingRange(ranked(1, 1000, 5), T0)).isEqualTo(150);
        assertThat(InMemoryMatchmaker.ratingRange(ranked(1, 1000, 3600), T0)).isEqualTo(600);
    }

    @Test
    void pairsClosestRatingWithinBothWindows() {
        InMemoryMatchmaker m = new InMemoryMatchmaker();
        m.join(ranked(1, 1000, 10));
        m.join(ranked(2, 1090, 0));
        m.join(ranked(3, 1040, 0));
        List<Matchmaker.Pair> pairs = m.pair(T0);
        assertThat(pairs).hasSize(1);
        assertThat(Set.of(pairs.getFirst().first().userId(), pairs.getFirst().second().userId())).containsExactlyInAnyOrder(1L, 3L);
        assertThat(m.waiting()).extracting(Matchmaker.Entry::userId).containsExactly(2L);
    }

    @Test
    void farApartPlayersWaitUntilWindowsWiden() {
        InMemoryMatchmaker m = new InMemoryMatchmaker();
        m.join(new Matchmaker.Entry(1, "darts", GameMode.RANKED, 1000, T0));
        m.join(new Matchmaker.Entry(2, "darts", GameMode.RANKED, 1300, T0));
        assertThat(m.pair(T0)).isEmpty();                       // window 100
        assertThat(m.pair(T0.plusSeconds(15))).isEmpty();       // window 250 < 300
        assertThat(m.pair(T0.plusSeconds(20))).hasSize(1);      // window 300
        assertThat(m.waiting()).isEmpty();
    }

    @Test
    void differentGamesAndModesNeverMix() {
        InMemoryMatchmaker m = new InMemoryMatchmaker();
        m.join(new Matchmaker.Entry(1, "darts", GameMode.RANKED, 1000, T0));
        m.join(new Matchmaker.Entry(2, "darts", GameMode.CASUAL, 1000, T0));
        m.join(new Matchmaker.Entry(3, "eight-ball", GameMode.RANKED, 1000, T0));
        assertThat(m.pair(T0)).isEmpty();
    }

    @Test
    void casualIsFirstComeFirstServedRegardlessOfRating() {
        InMemoryMatchmaker m = new InMemoryMatchmaker();
        m.join(new Matchmaker.Entry(1, "darts", GameMode.CASUAL, 1500, T0.minusSeconds(3)));
        m.join(new Matchmaker.Entry(2, "darts", GameMode.CASUAL, 700, T0.minusSeconds(2)));
        m.join(new Matchmaker.Entry(3, "darts", GameMode.CASUAL, 1500, T0.minusSeconds(1)));
        List<Matchmaker.Pair> pairs = m.pair(T0);
        assertThat(pairs).singleElement().satisfies(p -> {
            assertThat(p.first().userId()).isEqualTo(1);
            assertThat(p.second().userId()).isEqualTo(2);
        });
    }

    @Test
    void rejoiningReplacesThePreviousQueueAndNobodyIsPairedTwice() {
        InMemoryMatchmaker m = new InMemoryMatchmaker();
        m.join(new Matchmaker.Entry(1, "darts", GameMode.RANKED, 1000, T0));
        m.join(new Matchmaker.Entry(1, "eight-ball", GameMode.RANKED, 1000, T0));
        assertThat(m.waiting()).hasSize(1);

        for (int i = 2; i <= 9; i++) m.join(new Matchmaker.Entry(i, "eight-ball", GameMode.RANKED, 1000 + i, T0));
        List<Matchmaker.Pair> pairs = m.pair(T0);
        Set<Long> users = pairs.stream().flatMap(p -> java.util.stream.Stream.of(p.first().userId(), p.second().userId()))
            .collect(Collectors.toSet());
        assertThat(pairs).hasSize(4);
        assertThat(users).hasSize(8);
        assertThat(m.waiting()).hasSize(1);
        long leftOver = m.waiting().getFirst().userId();
        assertThat(users).doesNotContain(leftOver);
        assertThat(m.leave(leftOver)).isPresent();
        assertThat(m.leave(leftOver)).isEmpty();
    }
}
