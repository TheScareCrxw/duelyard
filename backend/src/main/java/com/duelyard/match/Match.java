package com.duelyard.match;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import com.duelyard.game.Game;
import com.duelyard.game.GameMode;
import com.duelyard.user.User;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A single game session between players. The aggregate root for its {@link MatchPlayer}s.
 *
 * <p>State transitions are guarded here and mirrored by database CHECK constraints. Optimistic
 * locking via {@code version} ensures two concurrent attempts to finish the same match cannot
 * both succeed.
 */
@Entity
@Table(name = "matches")
public class Match {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false, updatable = false)
    private Game game;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private GameMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MatchStatus status = MatchStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 16)
    private MatchEndReason endReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "ratings_applied_at")
    private Instant ratingsAppliedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seat ASC")
    private List<MatchPlayer> players = new ArrayList<>();

    protected Match() {
    }

    public Match(Game game, GameMode mode) {
        this.game = Objects.requireNonNull(game, "game");
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    public MatchPlayer addPlayer(User user, int seat) {
        requireStatus(MatchStatus.PENDING, "add players");
        Objects.requireNonNull(user.getId(), "user must be persisted");
        boolean taken = players.stream()
            .anyMatch(p -> p.getSeat() == seat || p.getUser().getId().equals(user.getId()));
        if (taken) {
            throw new IllegalStateException("Seat or user already present in match " + id);
        }
        MatchPlayer player = new MatchPlayer(this, user, seat);
        players.add(player);
        return player;
    }

    /** Records a player's server-determined outcome. Allowed only once the match has completed. */
    public void recordResult(Long userId, MatchResult result, Integer score) {
        requireStatus(MatchStatus.COMPLETED, "record results");
        player(userId).recordOutcome(result, score);
    }

    public void recordRatingChange(Long userId, int before, int after) {
        requireStatus(MatchStatus.COMPLETED, "record rating changes");
        player(userId).recordRatingChange(before, after);
    }

    public MatchPlayer player(Long userId) {
        return players.stream()
            .filter(p -> p.getUser().getId().equals(userId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("User " + userId + " is not in match " + id));
    }

    public void start(Instant now) {
        requireStatus(MatchStatus.PENDING, "start");
        this.status = MatchStatus.IN_PROGRESS;
        this.startedAt = Objects.requireNonNull(now, "now");
    }

    /** Ends the match. {@code COMPLETED} is reachable only from IN_PROGRESS. */
    public void end(MatchStatus terminalStatus, MatchEndReason reason, Instant now) {
        if (!terminalStatus.isTerminal()) {
            throw new IllegalArgumentException(terminalStatus + " is not a terminal status");
        }
        if (status.isTerminal()) {
            throw new IllegalStateException("Match " + id + " has already ended (" + status + ")");
        }
        if (terminalStatus == MatchStatus.COMPLETED && status != MatchStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only an in-progress match can be completed");
        }
        this.status = terminalStatus;
        this.endReason = reason;
        this.endedAt = Objects.requireNonNull(now, "now");
    }

    /** Marks ratings as applied. Throws if they already were, so ratings can never be applied twice. */
    public void markRatingsApplied(Instant now) {
        if (mode != GameMode.RANKED || status != MatchStatus.COMPLETED) {
            throw new IllegalStateException("Ratings apply only to completed ranked matches");
        }
        if (ratingsAppliedAt != null) {
            throw new IllegalStateException("Ratings already applied for match " + id);
        }
        this.ratingsAppliedAt = Objects.requireNonNull(now, "now");
    }

    private void requireStatus(MatchStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException("Cannot " + action + " when match is " + status);
        }
    }

    public UUID getId() { return id; }
    public Game getGame() { return game; }
    public GameMode getMode() { return mode; }
    public MatchStatus getStatus() { return status; }
    public MatchEndReason getEndReason() { return endReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public Instant getRatingsAppliedAt() { return ratingsAppliedAt; }
    public List<MatchPlayer> getPlayers() { return Collections.unmodifiableList(players); }
}
