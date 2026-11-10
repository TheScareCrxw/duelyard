package com.duelyard.multiplayer;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.duelyard.game.GameMode;
import com.duelyard.game.GameModule;
import com.duelyard.game.engine.InvalidActionException;
import com.duelyard.game.engine.TwoPlayerOutcomes;
import com.duelyard.match.MatchCompletion;
import com.duelyard.match.MatchCompletionService;
import com.duelyard.match.MatchEndReason;
import com.duelyard.match.MatchLifecycleService;
import com.duelyard.match.MatchResult;
import com.duelyard.match.PlayerOutcome;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs live matches. The server is the only authority: clients send inputs, the room passes them
 * to the game session, and only the session's outcome ends a match (through
 * {@link MatchCompletionService}, which records results and ratings exactly once).
 *
 * <p>Rooms live in memory on this instance; see docs/games.md for how this scales out.
 */
@Service
public class MatchRoomService {

    private static final Logger log = LoggerFactory.getLogger(MatchRoomService.class);

    private final Map<UUID, MatchRoom> rooms = new ConcurrentHashMap<>();
    private final Map<Long, UUID> activeByUser = new ConcurrentHashMap<>();
    private final ConnectionRegistry connections;
    private final MatchLifecycleService lifecycle;
    private final MatchCompletionService completion;
    private final RealtimeProperties properties;
    private final ScheduledExecutorService scheduler;
    private final MatchRandomFactory randomFactory;
    private final Clock clock;
    private final JsonMapper json;

    public MatchRoomService(ConnectionRegistry connections, MatchLifecycleService lifecycle,
            MatchCompletionService completion, RealtimeProperties properties, ScheduledExecutorService realtimeScheduler,
            MatchRandomFactory randomFactory, Clock clock, JsonMapper json) {
        this.connections = connections;
        this.lifecycle = lifecycle;
        this.completion = completion;
        this.properties = properties;
        this.scheduler = realtimeScheduler;
        this.randomFactory = randomFactory;
        this.clock = clock;
        this.json = json;
    }

    public record Player(long userId, String username, String avatarUrl, int rating) {
    }

    public Optional<UUID> activeMatch(long userId) {
        return Optional.ofNullable(activeByUser.get(userId));
    }

    public boolean isInMatch(long userId) {
        return activeByUser.containsKey(userId);
    }

    // --- lifecycle -----------------------------------------------------------------------------

    /** Creates a room for two paired players, announces it, and starts it after the countdown. */
    public UUID createRoom(GameModule module, GameMode mode, List<Player> players) {
        RandomGenerator random = randomFactory.create();
        List<MatchRoom.Seat> seats = new ArrayList<>(players.stream()
            .map(p -> new MatchRoom.Seat(p.userId(), p.username(), p.avatarUrl(), p.rating())).toList());
        if (random.nextBoolean()) Collections.reverse(seats);   // who goes first is random

        UUID matchId = lifecycle.create(module.definition().slug(), mode, seats.stream().map(MatchRoom.Seat::userId).toList());
        long startsAt = clock.millis() + properties.countdown().toMillis();
        MatchRoom room = new MatchRoom(matchId, module, mode, List.copyOf(seats), module.newSession(random), startsAt);
        rooms.put(matchId, room);
        seats.forEach(s -> activeByUser.put(s.userId(), matchId));

        room.lock.lock();
        try {
            for (int seat = 0; seat < 2; seat++) {
                long userId = seats.get(seat).userId();
                connections.send(userId, new Messages.MatchFound(matchId, room.game(), mode.name(), summary(room, 1 - seat), startsAt));
                connections.send(userId, new Messages.MatchState(state(room, seat)));
                if (!connections.isConnected(userId)) onDisconnected(userId);
            }
        } finally {
            room.lock.unlock();
        }
        scheduler.schedule(() -> start(room), properties.countdown().toMillis(), TimeUnit.MILLISECONDS);
        log.info("Match {} created: {} {} {} vs {}", matchId, room.game(), mode, seats.get(0).username(), seats.get(1).username());
        return matchId;
    }

    private void start(MatchRoom room) {
        room.lock.lock();
        try {
            if (room.phase != MatchRoom.Phase.COUNTDOWN) return;
            lifecycle.start(room.matchId);
            room.phase = MatchRoom.Phase.PLAYING;
            record(room, null, "start", Map.of("seats", room.seats.stream().map(MatchRoom.Seat::username).toList()));
            startTurnTimer(room);
            broadcastState(room);
        } catch (RuntimeException e) {
            log.error("Failed to start match {}", room.matchId, e);
            abandon(room);
        } finally {
            room.lock.unlock();
        }
    }

    // --- moves ---------------------------------------------------------------------------------

    public void handleAction(long userId, UUID matchId, JsonNode input) {
        MatchRoom room = matchId == null ? null : rooms.get(matchId);
        int seat = room == null ? -1 : room.seatOf(userId);
        if (seat < 0) {
            connections.send(userId, new Messages.ErrorMessage("NO_SUCH_MATCH", "You're not in that match."));
            return;
        }
        room.lock.lock();
        try {
            if (room.phase != MatchRoom.Phase.PLAYING) {
                connections.send(userId, new Messages.ErrorMessage("MATCH_NOT_ACTIVE", "The match isn't in play."));
                return;
            }
            List<Object> events;
            try {
                events = room.session.applyAction(seat, input);
            } catch (InvalidActionException e) {
                connections.send(userId, new Messages.ErrorMessage(e.code(), e.getMessage()));
                return;
            }
            room.consecutiveTimeouts[seat] = 0;
            record(room, userId, "action", eventPayload(seat, input, events));
            afterMove(room, seat, false, events);
        } finally {
            room.lock.unlock();
        }
    }

    private void afterMove(MatchRoom room, int actorSeat, boolean timedOut, List<Object> events) {
        Optional<List<MatchResult>> outcome = room.session.outcome();
        if (outcome.isPresent()) {
            cancelTurnTimer(room);
            broadcastEvents(room, actorSeat, timedOut, events);
            finish(room, outcome.get(), MatchEndReason.NORMAL);
        } else {
            startTurnTimer(room);
            broadcastEvents(room, actorSeat, timedOut, events);
        }
    }

    private void startTurnTimer(MatchRoom room) {
        cancelTurnTimer(room);
        if (room.session.turnSeat() < 0) return;
        long version = ++room.turnVersion;
        Duration limit = properties.turnLimitOverride() != null ? properties.turnLimitOverride() : room.module.turnTimeLimit();
        room.turnDeadline = clock.millis() + limit.toMillis();
        room.turnTimer = scheduler.schedule(() -> onTurnTimeout(room, version), limit.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void cancelTurnTimer(MatchRoom room) {
        if (room.turnTimer != null) room.turnTimer.cancel(false);
        room.turnTimer = null;
        room.turnVersion++;
    }

    private void onTurnTimeout(MatchRoom room, long version) {
        room.lock.lock();
        try {
            if (room.phase != MatchRoom.Phase.PLAYING || version != room.turnVersion) return;
            int seat = room.session.turnSeat();
            if (seat < 0) return;
            List<Object> events = room.session.onTurnTimeout(seat);
            record(room, room.seats.get(seat).userId(), "timeout", eventPayload(seat, null, events));
            if (++room.consecutiveTimeouts[seat] >= properties.maxConsecutiveTimeouts() && room.session.outcome().isEmpty()) {
                cancelTurnTimer(room);
                broadcastEvents(room, seat, true, events);
                forfeit(room, seat, MatchEndReason.TIMEOUT);
            } else {
                afterMove(room, seat, true, events);
            }
        } catch (RuntimeException e) {
            log.error("Turn timeout handling failed for match {}", room.matchId, e);
        } finally {
            room.lock.unlock();
        }
    }

    public void sync(long userId, UUID matchId) {
        MatchRoom room = matchId == null ? null : rooms.get(matchId);
        int seat = room == null ? -1 : room.seatOf(userId);
        if (seat < 0) {
            connections.send(userId, new Messages.ErrorMessage("NO_SUCH_MATCH", "That match isn't available."));
            return;
        }
        room.lock.lock();
        try {
            connections.send(userId, new Messages.MatchState(state(room, seat)));
        } finally {
            room.lock.unlock();
        }
    }

    // --- connections ---------------------------------------------------------------------------

    public void onConnected(long userId) {
        MatchRoom room = roomOf(userId);
        if (room == null) return;
        room.lock.lock();
        try {
            int seat = room.seatOf(userId);
            if (room.phase == MatchRoom.Phase.ENDED) return;
            if (!room.connected[seat]) {
                room.connected[seat] = true;
                if (room.graceTimers[seat] != null) room.graceTimers[seat].cancel(false);
                room.graceTimers[seat] = null;
                connections.send(room.seats.get(1 - seat).userId(), new Messages.OpponentConnection(room.matchId, true, 0));
            }
            connections.send(userId, new Messages.MatchState(state(room, seat)));
        } finally {
            room.lock.unlock();
        }
    }

    public void onDisconnected(long userId) {
        MatchRoom room = roomOf(userId);
        if (room == null) return;
        boolean locked = room.lock.isHeldByCurrentThread();
        if (!locked) room.lock.lock();
        try {
            int seat = room.seatOf(userId);
            if (room.phase == MatchRoom.Phase.ENDED || !room.connected[seat]) return;
            room.connected[seat] = false;
            Duration grace = properties.disconnectGrace();
            connections.send(room.seats.get(1 - seat).userId(),
                new Messages.OpponentConnection(room.matchId, false, grace.toSeconds()));
            room.graceTimers[seat] = scheduler.schedule(() -> graceExpired(room, seat), grace.toMillis(), TimeUnit.MILLISECONDS);
        } finally {
            if (!locked) room.lock.unlock();
        }
    }

    private void graceExpired(MatchRoom room, int seat) {
        room.lock.lock();
        try {
            if (room.phase == MatchRoom.Phase.ENDED || room.connected[seat]) return;
            if (!room.connected[1 - seat]) {
                abandon(room);
            } else {
                forfeit(room, seat, MatchEndReason.DISCONNECT);
            }
        } catch (RuntimeException e) {
            log.error("Disconnect handling failed for match {}", room.matchId, e);
        } finally {
            room.lock.unlock();
        }
    }

    // --- ending --------------------------------------------------------------------------------

    private void forfeit(MatchRoom room, int loserSeat, MatchEndReason reason) {
        finish(room, TwoPlayerOutcomes.winner(1 - loserSeat), reason);
    }

    private void finish(MatchRoom room, List<MatchResult> results, MatchEndReason reason) {
        if (room.phase == MatchRoom.Phase.ENDED) return;
        boolean wasCountdown = room.phase == MatchRoom.Phase.COUNTDOWN;
        endRoom(room);

        List<Messages.SeatResult> seatResults = new ArrayList<>();
        try {
            if (wasCountdown) lifecycle.start(room.matchId);
            List<PlayerOutcome> outcomes = new ArrayList<>();
            for (int seat = 0; seat < 2; seat++) {
                outcomes.add(new PlayerOutcome(room.seats.get(seat).userId(), results.get(seat), null));
            }
            MatchCompletion done = completion.complete(room.matchId, outcomes, reason);
            for (int seat = 0; seat < 2; seat++) {
                MatchRoom.Seat s = room.seats.get(seat);
                MatchCompletion.PlayerChange change = done.players().stream()
                    .filter(p -> p.userId() == s.userId()).findFirst().orElseThrow();
                seatResults.add(new Messages.SeatResult(seat, s.username(), change.result().name(),
                    change.ratingBefore(), change.ratingAfter()));
            }
        } catch (RuntimeException e) {
            log.error("Failed to record result of match {}", room.matchId, e);
            for (int seat = 0; seat < 2; seat++) {
                seatResults.add(new Messages.SeatResult(seat, room.seats.get(seat).username(), results.get(seat).name(), null, null));
            }
        }
        record(room, null, "end", Map.of("reason", reason.name(), "results", results));
        room.seats.forEach(s -> connections.send(s.userId(), new Messages.MatchEnded(room.matchId, reason.name(), seatResults)));
        log.info("Match {} ended: {} {}", room.matchId, reason, results);
    }

    private void abandon(MatchRoom room) {
        if (room.phase == MatchRoom.Phase.ENDED) return;
        endRoom(room);
        try {
            completion.abandon(room.matchId, MatchEndReason.DISCONNECT);
        } catch (RuntimeException e) {
            log.error("Failed to abandon match {}", room.matchId, e);
        }
        List<Messages.SeatResult> none = new ArrayList<>();
        for (int seat = 0; seat < 2; seat++) {
            none.add(new Messages.SeatResult(seat, room.seats.get(seat).username(), null, null, null));
        }
        room.seats.forEach(s -> connections.send(s.userId(), new Messages.MatchEnded(room.matchId, "ABANDONED", none)));
        log.info("Match {} abandoned", room.matchId);
    }

    private void endRoom(MatchRoom room) {
        room.phase = MatchRoom.Phase.ENDED;
        cancelTurnTimer(room);
        for (int seat = 0; seat < 2; seat++) {
            if (room.graceTimers[seat] != null) room.graceTimers[seat].cancel(false);
            activeByUser.remove(room.seats.get(seat).userId(), room.matchId);
        }
        scheduler.schedule(() -> rooms.remove(room.matchId), properties.endedRoomRetention().toMillis(), TimeUnit.MILLISECONDS);
    }

    // --- helpers -------------------------------------------------------------------------------

    private MatchRoom roomOf(long userId) {
        UUID id = activeByUser.get(userId);
        return id == null ? null : rooms.get(id);
    }

    private void broadcastState(MatchRoom room) {
        for (int seat = 0; seat < 2; seat++) {
            connections.send(room.seats.get(seat).userId(), new Messages.MatchState(state(room, seat)));
        }
    }

    private void broadcastEvents(MatchRoom room, int actorSeat, boolean timedOut, List<Object> events) {
        for (int seat = 0; seat < 2; seat++) {
            connections.send(room.seats.get(seat).userId(),
                new Messages.MatchEvents(room.matchId, actorSeat, timedOut, events, state(room, seat)));
        }
    }

    private Messages.RoomState state(MatchRoom room, int seat) {
        boolean playing = room.phase == MatchRoom.Phase.PLAYING;
        List<Messages.PlayerSummary> players = List.of(summary(room, 0), summary(room, 1));
        return new Messages.RoomState(room.matchId, room.game(), room.mode.name(), room.phase.name(), seat, players,
            room.startsAt, playing ? room.session.turnSeat() : -1, playing ? room.turnDeadline : null, room.session.view(seat));
    }

    private Messages.PlayerSummary summary(MatchRoom room, int seat) {
        MatchRoom.Seat s = room.seats.get(seat);
        return new Messages.PlayerSummary(seat, s.username(), s.avatarUrl(), s.rating(), room.connected[seat]);
    }

    private static Map<String, Object> eventPayload(int seat, JsonNode input, List<Object> events) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("seat", seat);
        if (input != null) payload.put("input", input);
        payload.put("events", events);
        return payload;
    }

    private void record(MatchRoom room, Long userId, String type, Object payload) {
        try {
            lifecycle.recordEvent(room.matchId, room.eventSequence++, userId, type, json.valueToTree(payload));
        } catch (RuntimeException e) {
            log.warn("Could not record {} event for match {}: {}", type, room.matchId, e.getMessage());
        }
    }
}
