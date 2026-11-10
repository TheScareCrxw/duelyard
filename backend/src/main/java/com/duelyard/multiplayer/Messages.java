package com.duelyard.multiplayer;

import java.util.List;
import java.util.UUID;

/** Server-to-client WebSocket messages. Every message has a {@code type}. See docs/games.md. */
public final class Messages {

    private Messages() {
    }

    public record PlayerSummary(int seat, String username, String avatarUrl, int rating, boolean connected) {
    }

    /** Full room snapshot, from the receiving player's seat. {@code turnDeadline} is epoch millis. */
    public record RoomState(UUID matchId, String game, String mode, String phase, int yourSeat,
                            List<PlayerSummary> players, long startsAt, int turnSeat, Long turnDeadline, Object view) {
    }

    public record Welcome(String type, String username, UUID activeMatchId) {
        public Welcome(String username, UUID activeMatchId) { this("welcome", username, activeMatchId); }
    }

    public record QueueStatus(String type, String game, String mode, long waitedSeconds, Integer ratingRange) {
        public QueueStatus(String game, String mode, long waitedSeconds, Integer ratingRange) {
            this("queue.status", game, mode, waitedSeconds, ratingRange);
        }
    }

    public record QueueLeft(String type, String reason) {
        public QueueLeft(String reason) { this("queue.left", reason); }
    }

    public record MatchFound(String type, UUID matchId, String game, String mode, PlayerSummary opponent, long startsAt) {
        public MatchFound(UUID matchId, String game, String mode, PlayerSummary opponent, long startsAt) {
            this("match.found", matchId, game, mode, opponent, startsAt);
        }
    }

    public record MatchState(String type, RoomState state) {
        public MatchState(RoomState state) { this("match.state", state); }
    }

    /** Events from one move (or timeout) followed by the resulting state. */
    public record MatchEvents(String type, UUID matchId, int actorSeat, boolean timedOut, List<Object> events, RoomState state) {
        public MatchEvents(UUID matchId, int actorSeat, boolean timedOut, List<Object> events, RoomState state) {
            this("match.events", matchId, actorSeat, timedOut, events, state);
        }
    }

    public record SeatResult(int seat, String username, String result, Integer ratingBefore, Integer ratingAfter) {
    }

    /** {@code reason}: NORMAL, FORFEIT, DISCONNECT, TIMEOUT, or ABANDONED (no result). */
    public record MatchEnded(String type, UUID matchId, String reason, List<SeatResult> results) {
        public MatchEnded(UUID matchId, String reason, List<SeatResult> results) {
            this("match.ended", matchId, reason, results);
        }
    }

    public record OpponentConnection(String type, UUID matchId, boolean connected, long graceSeconds) {
        public OpponentConnection(UUID matchId, boolean connected, long graceSeconds) {
            this("opponent.connection", matchId, connected, graceSeconds);
        }
    }

    public record ErrorMessage(String type, String code, String message) {
        public ErrorMessage(String code, String message) { this("error", code, message); }
    }

    public record Pong(String type) {
        public Pong() { this("pong"); }
    }
}
