package com.duelyard.rating;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.duelyard.game.GameMode;
import com.duelyard.match.Match;
import com.duelyard.match.MatchPlayer;
import com.duelyard.match.MatchStatus;

/**
 * Applies a completed match's results to players' ratings and records. Must run inside the
 * transaction that completes the match ({@link Propagation#MANDATORY}), so the match result,
 * ratings and history commit or roll back together.
 *
 * <p>Ranked: Elo update, peak rating, ranked W/L/D, one {@link RatingHistory} row per player and
 * {@code ratings_applied_at} on the match. Casual: casual W/L/D only.
 *
 * <p>Double application is prevented three ways: the caller holds a row lock on the match,
 * {@link Match#markRatingsApplied} refuses a second call, and the database allows only one
 * rating_history row per (match, player).
 */
@Service
public class RatingService {

    private static final Logger log = LoggerFactory.getLogger(RatingService.class);

    private final PlayerGameStatsRepository stats;
    private final RatingHistoryRepository history;
    private final Clock clock;

    public RatingService(PlayerGameStatsRepository stats, RatingHistoryRepository history, Clock clock) {
        this.stats = stats;
        this.history = history;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void applyResults(Match match) {
        if (match.getStatus() != MatchStatus.COMPLETED) {
            throw new IllegalStateException("Only completed matches affect ratings or records");
        }
        Map<Long, PlayerGameStats> byUser = lockStats(match);

        if (match.getMode() == GameMode.CASUAL) {
            match.getPlayers().forEach(p -> byUser.get(p.getUser().getId()).recordCasual(p.getResult()));
            return;
        }
        applyElo(match, byUser);
    }

    private void applyElo(Match match, Map<Long, PlayerGameStats> byUser) {
        List<MatchPlayer> players = match.getPlayers();
        if (players.size() != 2) {
            throw new IllegalStateException("Elo is implemented for two-player matches; got " + players.size());
        }
        MatchPlayer a = players.get(0);
        MatchPlayer b = players.get(1);
        PlayerGameStats statsA = byUser.get(a.getUser().getId());
        PlayerGameStats statsB = byUser.get(b.getUser().getId());

        int beforeA = statsA.getRating();
        int beforeB = statsB.getRating();
        int deltaA = EloCalculator.delta(beforeA, beforeB, a.getResult().score());
        int afterA = beforeA + deltaA;
        int afterB = beforeB - deltaA;

        match.markRatingsApplied(clock.instant());
        statsA.recordRanked(a.getResult(), afterA);
        statsB.recordRanked(b.getResult(), afterB);
        match.recordRatingChange(a.getUser().getId(), beforeA, afterA);
        match.recordRatingChange(b.getUser().getId(), beforeB, afterB);
        history.save(new RatingHistory(a.getUser(), match.getGame(), match, beforeA, afterA));
        history.save(new RatingHistory(b.getUser(), match.getGame(), match, beforeB, afterB));

        log.info("Ratings applied match={} game={} {}:{}->{} {}:{}->{}", match.getId(), match.getGame().getSlug(),
            a.getUser().getId(), beforeA, afterA, b.getUser().getId(), beforeB, afterB);
    }

    /** Locks (creating if missing, e.g. for a game added after the player joined) each player's stats row. */
    private Map<Long, PlayerGameStats> lockStats(Match match) {
        List<Long> userIds = match.getPlayers().stream().map(p -> p.getUser().getId()).sorted().toList();
        Long gameId = match.getGame().getId();
        List<PlayerGameStats> locked = stats.lockForUpdate(userIds, gameId);
        if (locked.size() < userIds.size()) {
            Map<Long, PlayerGameStats> present = locked.stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), Function.identity()));
            match.getPlayers().stream()
                .filter(p -> !present.containsKey(p.getUser().getId()))
                .forEach(p -> stats.save(new PlayerGameStats(p.getUser(), match.getGame())));
            stats.flush();
            locked = stats.lockForUpdate(userIds, gameId);
        }
        return locked.stream().collect(Collectors.toMap(s -> s.getUser().getId(), Function.identity()));
    }
}
