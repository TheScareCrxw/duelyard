package com.duelyard.match;

import java.time.Clock;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duelyard.common.error.NotFoundException;
import com.duelyard.rating.RatingService;

/**
 * The only way a match ends. Called by the server-side game logic (never with data taken
 * directly from a client) once it has determined the outcome.
 *
 * <p>Each call runs in one transaction holding a row lock on the match: the match status, every
 * player's result, the rating changes and the rating history commit together or not at all.
 * Completing a match that is already completed is a no-op that reports {@code alreadyCompleted}.
 */
@Service
public class MatchCompletionService {

    private final MatchRepository matches;
    private final RatingService ratings;
    private final Clock clock;

    public MatchCompletionService(MatchRepository matches, RatingService ratings, Clock clock) {
        this.matches = matches;
        this.ratings = ratings;
        this.clock = clock;
    }

    @Transactional
    public MatchCompletion complete(UUID matchId, Collection<PlayerOutcome> outcomes, MatchEndReason reason) {
        Match match = matches.lockById(matchId).orElseThrow(() -> new NotFoundException("No match " + matchId));

        if (match.getStatus() == MatchStatus.COMPLETED) {
            return MatchCompletion.of(match, true);
        }
        if (match.getStatus() != MatchStatus.IN_PROGRESS) {
            throw new IllegalStateException("Match " + matchId + " is " + match.getStatus() + " and can't be completed");
        }
        Map<Long, PlayerOutcome> byUser = validate(match, outcomes);

        match.end(MatchStatus.COMPLETED, reason, clock.instant());
        byUser.values().forEach(o -> match.recordResult(o.userId(), o.result(), o.score()));
        ratings.applyResults(match);
        matches.flush();
        return MatchCompletion.of(match, false);
    }

    /** Ends a match without a result (e.g. both players left). Nobody's rating or record changes. */
    @Transactional
    public void abandon(UUID matchId, MatchEndReason reason) {
        Match match = matches.lockById(matchId).orElseThrow(() -> new NotFoundException("No match " + matchId));
        if (match.getStatus().isTerminal()) {
            return;
        }
        MatchStatus terminal = match.getStatus() == MatchStatus.PENDING ? MatchStatus.CANCELLED : MatchStatus.ABANDONED;
        match.end(terminal, reason, clock.instant());
    }

    /** Every participant exactly once, and results that make sense together. */
    private static Map<Long, PlayerOutcome> validate(Match match, Collection<PlayerOutcome> outcomes) {
        Map<Long, PlayerOutcome> byUser = outcomes.stream()
            .collect(Collectors.toMap(PlayerOutcome::userId, Function.identity(), (x, y) -> {
                throw new IllegalArgumentException("Duplicate outcome for user " + x.userId());
            }));
        Set<Long> participants = match.getPlayers().stream().map(p -> p.getUser().getId()).collect(Collectors.toSet());
        if (!byUser.keySet().equals(participants)) {
            throw new IllegalArgumentException("Outcomes must cover exactly the match's players " + participants);
        }

        Map<MatchResult, Long> counts = new EnumMap<>(MatchResult.class);
        byUser.values().forEach(o -> counts.merge(o.result(), 1L, Long::sum));
        boolean allDraw = counts.getOrDefault(MatchResult.DRAW, 0L) == participants.size();
        boolean oneWinner = counts.getOrDefault(MatchResult.WIN, 0L) == 1
            && counts.getOrDefault(MatchResult.LOSS, 0L) == participants.size() - 1;
        if (!allDraw && !oneWinner) {
            throw new IllegalArgumentException("Inconsistent results: " + counts);
        }
        return byUser;
    }
}
