package com.duelyard.multiplayer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;

import com.duelyard.game.GameMode;
import com.duelyard.game.GameModule;
import com.duelyard.game.engine.GameSession;

/**
 * A live match: the game session plus who is in it and its timers. All mutation happens while
 * holding {@link #lock}, so moves, timeouts and disconnects for one match never interleave.
 */
final class MatchRoom {

    enum Phase { COUNTDOWN, PLAYING, ENDED }

    record Seat(long userId, String username, String avatarUrl, int rating) {
    }

    final UUID matchId;
    final GameModule module;
    final GameMode mode;
    final List<Seat> seats;
    final GameSession session;
    final long startsAt;
    final ReentrantLock lock = new ReentrantLock();

    Phase phase = Phase.COUNTDOWN;
    final boolean[] connected = {true, true};
    final int[] consecutiveTimeouts = {0, 0};
    final ScheduledFuture<?>[] graceTimers = new ScheduledFuture<?>[2];
    ScheduledFuture<?> turnTimer;
    long turnDeadline;
    /** Incremented whenever the turn changes, so a stale timer can tell it no longer applies. */
    long turnVersion;
    int eventSequence;

    MatchRoom(UUID matchId, GameModule module, GameMode mode, List<Seat> seats, GameSession session, long startsAt) {
        this.matchId = matchId;
        this.module = module;
        this.mode = mode;
        this.seats = seats;
        this.session = session;
        this.startsAt = startsAt;
    }

    int seatOf(long userId) {
        for (int i = 0; i < seats.size(); i++) {
            if (seats.get(i).userId() == userId) return i;
        }
        return -1;
    }

    String game() {
        return module.definition().slug();
    }
}
