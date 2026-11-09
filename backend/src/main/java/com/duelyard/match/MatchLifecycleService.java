package com.duelyard.match;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duelyard.common.error.NotFoundException;
import com.duelyard.game.GameMode;
import com.duelyard.game.GameRepository;
import com.duelyard.user.UserRepository;

import tools.jackson.databind.JsonNode;

/** Creating and starting matches, and recording their event log. Ending is {@link MatchCompletionService}. */
@Service
public class MatchLifecycleService {

    private final MatchRepository matches;
    private final MatchEventRepository events;
    private final GameRepository games;
    private final UserRepository users;
    private final Clock clock;

    public MatchLifecycleService(MatchRepository matches, MatchEventRepository events, GameRepository games,
            UserRepository users, Clock clock) {
        this.matches = matches;
        this.events = events;
        this.games = games;
        this.users = users;
        this.clock = clock;
    }

    /** A new PENDING match with the given players in seat order. */
    @Transactional
    public UUID create(String gameSlug, GameMode mode, List<Long> userIdsBySeat) {
        Match match = new Match(games.findBySlug(gameSlug).orElseThrow(() -> new NotFoundException(gameSlug)), mode);
        for (int seat = 0; seat < userIdsBySeat.size(); seat++) {
            match.addPlayer(users.getReferenceById(userIdsBySeat.get(seat)), seat);
        }
        return matches.saveAndFlush(match).getId();
    }

    @Transactional
    public void start(UUID matchId) {
        Match match = matches.lockById(matchId).orElseThrow(() -> new NotFoundException("No match " + matchId));
        if (match.getStatus() == MatchStatus.PENDING) {
            match.start(clock.instant());
        }
    }

    /** Appends to the match's audit log. {@code sequence} must be unique per match (enforced by the database). */
    @Transactional
    public void recordEvent(UUID matchId, int sequence, Long userId, String type, JsonNode payload) {
        Match match = matches.getReferenceById(matchId);
        events.save(new MatchEvent(match, sequence, userId == null ? null : users.getReferenceById(userId), type,
            payload == null ? null : payload.toString()));
    }
}
