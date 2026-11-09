package com.duelyard.rating;

import java.time.Instant;
import java.util.Objects;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import com.duelyard.game.Game;
import com.duelyard.match.Match;
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

/**
 * Append-only record of one rating change caused by one ranked match. The database enforces at
 * most one row per (match, user), which makes duplicate rating updates impossible.
 */
@Entity
@Immutable
@Table(name = "rating_history")
public class RatingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Column(name = "rating_before", nullable = false)
    private int ratingBefore;

    @Column(name = "rating_after", nullable = false)
    private int ratingAfter;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RatingHistory() {
    }

    public RatingHistory(User user, Game game, Match match, int ratingBefore, int ratingAfter) {
        this.user = Objects.requireNonNull(user, "user");
        this.game = Objects.requireNonNull(game, "game");
        this.match = Objects.requireNonNull(match, "match");
        this.ratingBefore = ratingBefore;
        this.ratingAfter = ratingAfter;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Game getGame() { return game; }
    public Match getMatch() { return match; }
    public int getRatingBefore() { return ratingBefore; }
    public int getRatingAfter() { return ratingAfter; }
    public int getDelta() { return ratingAfter - ratingBefore; }
    public Instant getCreatedAt() { return createdAt; }
}
