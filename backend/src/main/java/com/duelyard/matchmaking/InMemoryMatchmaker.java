package com.duelyard.matchmaking;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.duelyard.game.GameMode;

/**
 * Queues in memory. All operations are synchronized: a pairing pass removes both players before
 * anyone else can see them, so nobody is ever matched twice.
 *
 * <p>Ranked pairing: oldest waiter first, paired with the closest-rated player whose difference
 * is within both players' current windows. A window starts at ±100 and grows by 50 every 5 s of
 * waiting, up to ±600. Casual: first come, first served.
 */
@Component
public class InMemoryMatchmaker implements Matchmaker {

    static final int INITIAL_RANGE = 100;
    static final int RANGE_STEP = 50;
    static final Duration STEP_INTERVAL = Duration.ofSeconds(5);
    static final int MAX_RANGE = 600;

    private final Map<Long, Entry> entries = new LinkedHashMap<>();

    public static int ratingRange(Entry entry, Instant now) {
        long waited = Math.max(0, Duration.between(entry.joinedAt(), now).toMillis());
        long steps = waited / STEP_INTERVAL.toMillis();
        return (int) Math.min(MAX_RANGE, INITIAL_RANGE + steps * RANGE_STEP);
    }

    @Override
    public synchronized void join(Entry entry) {
        entries.remove(entry.userId());
        entries.put(entry.userId(), entry);
    }

    @Override
    public synchronized Optional<Entry> leave(long userId) {
        return Optional.ofNullable(entries.remove(userId));
    }

    @Override
    public synchronized Optional<Entry> find(long userId) {
        return Optional.ofNullable(entries.get(userId));
    }

    @Override
    public synchronized List<Entry> waiting() {
        return List.copyOf(entries.values());
    }

    @Override
    public synchronized List<Pair> pair(Instant now) {
        List<Pair> pairs = new ArrayList<>();
        Map<String, List<Entry>> queues = new LinkedHashMap<>();
        for (Entry e : entries.values()) {
            queues.computeIfAbsent(e.game() + "/" + e.mode(), k -> new ArrayList<>()).add(e);
        }

        for (List<Entry> queue : queues.values()) {
            queue.sort(Comparator.comparing(Entry::joinedAt).thenComparingLong(Entry::userId));
            Set<Long> taken = new HashSet<>();
            for (Entry a : queue) {
                if (taken.contains(a.userId())) continue;
                Entry best = null;
                for (Entry b : queue) {
                    if (b == a || taken.contains(b.userId())) continue;
                    if (a.mode() == GameMode.CASUAL) {
                        best = b;
                        break;
                    }
                    int diff = Math.abs(a.rating() - b.rating());
                    if (diff > Math.min(ratingRange(a, now), ratingRange(b, now))) continue;
                    if (best == null || diff < Math.abs(a.rating() - best.rating())) best = b;
                }
                if (best != null) {
                    taken.add(a.userId());
                    taken.add(best.userId());
                    pairs.add(new Pair(a, best));
                }
            }
        }
        for (Pair p : pairs) {
            entries.remove(p.first().userId());
            entries.remove(p.second().userId());
        }
        return pairs;
    }
}
