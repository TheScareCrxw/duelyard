package com.duelyard.matchmaking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.duelyard.game.GameMode;

/**
 * Queue storage and pairing. The in-memory implementation serves a single backend instance; a
 * Redis implementation (sorted set per queue, score = rating) can replace it to scale out.
 */
public interface Matchmaker {

    record Entry(long userId, String game, GameMode mode, int rating, Instant joinedAt) {
    }

    record Pair(Entry first, Entry second) {
    }

    /** Adds the user to a queue, replacing any queue they were already in. */
    void join(Entry entry);

    /** Removes the user from whichever queue they're in. */
    Optional<Entry> leave(long userId);

    Optional<Entry> find(long userId);

    /** Removes and returns every pair that can be matched now. Each user appears in at most one pair. */
    List<Pair> pair(Instant now);

    List<Entry> waiting();
}
