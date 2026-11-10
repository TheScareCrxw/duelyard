package com.duelyard.matchmaking;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duelyard.game.GameAvailability;
import com.duelyard.game.GameMode;
import com.duelyard.game.GameModule;
import com.duelyard.game.GameRegistry;
import com.duelyard.game.GameRepository;
import com.duelyard.multiplayer.ConnectionRegistry;
import com.duelyard.multiplayer.MatchRoomService;
import com.duelyard.multiplayer.Messages;
import com.duelyard.rating.PlayerGameStats;
import com.duelyard.rating.PlayerGameStatsRepository;
import com.duelyard.user.User;
import com.duelyard.user.UserRepository;

/**
 * Game-agnostic matchmaking: validates queue requests, pairs players once a second and hands each
 * pair to {@link MatchRoomService}. Knows nothing about any specific game's rules.
 */
@Service
public class MatchmakingService {

    private static final Logger log = LoggerFactory.getLogger(MatchmakingService.class);

    private final Matchmaker matchmaker;
    private final GameRegistry registry;
    private final GameRepository games;
    private final UserRepository users;
    private final PlayerGameStatsRepository stats;
    private final MatchRoomService rooms;
    private final ConnectionRegistry connections;
    private final Clock clock;

    public MatchmakingService(Matchmaker matchmaker, GameRegistry registry, GameRepository games, UserRepository users,
            PlayerGameStatsRepository stats, MatchRoomService rooms, ConnectionRegistry connections, Clock clock) {
        this.matchmaker = matchmaker;
        this.registry = registry;
        this.games = games;
        this.users = users;
        this.stats = stats;
        this.rooms = rooms;
        this.connections = connections;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public void join(long userId, String game, String modeName) {
        Optional<GameModule> module = registry.find(game == null ? "" : game);
        GameMode mode = parseMode(modeName);
        if (module.isEmpty() || mode == null || !module.get().definition().modes().contains(mode)) {
            connections.send(userId, new Messages.ErrorMessage("INVALID_QUEUE", "Unknown game or mode."));
            return;
        }
        if (module.get().definition().availability() != GameAvailability.AVAILABLE) {
            connections.send(userId, new Messages.ErrorMessage("GAME_UNAVAILABLE", "That game isn't playable yet."));
            return;
        }
        if (rooms.isInMatch(userId)) {
            connections.send(userId, new Messages.ErrorMessage("ALREADY_IN_MATCH", "Finish your current match first."));
            return;
        }
        int rating = games.findBySlug(game)
            .flatMap(g -> stats.findByUserIdAndGameId(userId, g.getId()))
            .map(PlayerGameStats::getRating)
            .orElse(PlayerGameStats.INITIAL_RATING);

        Matchmaker.Entry entry = new Matchmaker.Entry(userId, game, mode, rating, clock.instant());
        matchmaker.join(entry);
        sendStatus(entry, entry.joinedAt());
        log.debug("User {} queued for {} {}", userId, game, mode);
    }

    public void leave(long userId, String reason) {
        if (matchmaker.leave(userId).isPresent()) {
            connections.send(userId, new Messages.QueueLeft(reason));
        }
    }

    public Optional<Matchmaker.Entry> queued(long userId) {
        return matchmaker.find(userId);
    }

    @Scheduled(fixedDelayString = "${app.matchmaking.tick:1000}")
    public void tick() {
        Instant now = clock.instant();
        for (Matchmaker.Pair pair : matchmaker.pair(now)) {
            try {
                startMatch(pair);
            } catch (RuntimeException e) {
                log.error("Could not start match for pair {}", pair, e);
                connections.send(pair.first().userId(), new Messages.ErrorMessage("MATCH_FAILED", "Couldn't start the match. Please queue again."));
                connections.send(pair.second().userId(), new Messages.ErrorMessage("MATCH_FAILED", "Couldn't start the match. Please queue again."));
            }
        }
        for (Matchmaker.Entry entry : matchmaker.waiting()) {
            sendStatus(entry, now);
        }
    }

    private void startMatch(Matchmaker.Pair pair) {
        GameModule module = registry.find(pair.first().game()).orElseThrow();
        List<MatchRoomService.Player> players = List.of(player(pair.first()), player(pair.second()));
        rooms.createRoom(module, pair.first().mode(), players);
    }

    private MatchRoomService.Player player(Matchmaker.Entry e) {
        User user = users.findById(e.userId()).orElseThrow();
        return new MatchRoomService.Player(user.getId(), user.getUsername(), user.getAvatarUrl(), e.rating());
    }

    private void sendStatus(Matchmaker.Entry entry, Instant now) {
        long waited = Math.max(0, (now.toEpochMilli() - entry.joinedAt().toEpochMilli()) / 1000);
        Integer range = entry.mode() == GameMode.RANKED ? InMemoryMatchmaker.ratingRange(entry, now) : null;
        connections.send(entry.userId(), new Messages.QueueStatus(entry.game(), entry.mode().name(), waited, range));
    }

    private static GameMode parseMode(String mode) {
        try {
            return mode == null ? null : GameMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
