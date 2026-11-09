package com.duelyard.rating;

import java.time.Instant;
import java.util.Objects;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.duelyard.game.Game;
import com.duelyard.match.MatchResult;
import com.duelyard.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A player's rating and win/loss record in one game. Ranked and casual records are kept apart
 * because only ranked play affects rating. Mutations arrive with the rating service (Phase 5).
 */
@Entity
@Table(name = "player_game_stats")
public class PlayerGameStats {

    public static final int INITIAL_RATING = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false, updatable = false)
    private Game game;

    @Column(nullable = false)
    private int rating = INITIAL_RATING;

    @Column(name = "peak_rating", nullable = false)
    private int peakRating = INITIAL_RATING;

    @Column(name = "ranked_wins", nullable = false)
    private int rankedWins;

    @Column(name = "ranked_losses", nullable = false)
    private int rankedLosses;

    @Column(name = "ranked_draws", nullable = false)
    private int rankedDraws;

    @Column(name = "casual_wins", nullable = false)
    private int casualWins;

    @Column(name = "casual_losses", nullable = false)
    private int casualLosses;

    @Column(name = "casual_draws", nullable = false)
    private int casualDraws;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected PlayerGameStats() {
    }

    public PlayerGameStats(User user, Game game) {
        this.user = Objects.requireNonNull(user, "user");
        this.game = Objects.requireNonNull(game, "game");
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Game getGame() { return game; }
    public int getRating() { return rating; }
    public int getPeakRating() { return peakRating; }
    public int getRankedWins() { return rankedWins; }
    public int getRankedLosses() { return rankedLosses; }
    public int getRankedDraws() { return rankedDraws; }
    public int getCasualWins() { return casualWins; }
    public int getCasualLosses() { return casualLosses; }
    public int getCasualDraws() { return casualDraws; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public int getRankedGamesPlayed() {
        return rankedWins + rankedLosses + rankedDraws;
    }

    /** Applies a ranked result: new rating, peak and ranked record. */
    public void recordRanked(MatchResult result, int newRating) {
        this.rating = newRating;
        this.peakRating = Math.max(peakRating, newRating);
        switch (result) {
            case WIN -> rankedWins++;
            case LOSS -> rankedLosses++;
            case DRAW -> rankedDraws++;
        }
    }

    /** Applies a casual result: record only, rating untouched. */
    public void recordCasual(MatchResult result) {
        switch (result) {
            case WIN -> casualWins++;
            case LOSS -> casualLosses++;
            case DRAW -> casualDraws++;
        }
    }
}
