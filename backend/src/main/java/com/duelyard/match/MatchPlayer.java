package com.duelyard.match;

import java.time.Instant;
import java.util.Objects;

import org.hibernate.annotations.CreationTimestamp;

import com.duelyard.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A participant in a match. Created only through {@link Match#addPlayer}. */
@Entity
@Table(name = "match_players")
public class MatchPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false, updatable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false, updatable = false)
    private short seat;

    @Enumerated(EnumType.STRING)
    @Column(length = 8)
    private MatchResult result;

    private Integer score;

    @Column(name = "rating_before")
    private Integer ratingBefore;

    @Column(name = "rating_after")
    private Integer ratingAfter;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    protected MatchPlayer() {
    }

    MatchPlayer(Match match, User user, int seat) {
        if (seat < 0 || seat > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid seat " + seat);
        }
        this.match = Objects.requireNonNull(match, "match");
        this.user = Objects.requireNonNull(user, "user");
        this.seat = (short) seat;
    }

    /** Records this player's outcome. Package-private: only match completion logic may set it. */
    void recordOutcome(MatchResult result, Integer score) {
        this.result = Objects.requireNonNull(result, "result");
        this.score = score;
    }

    void recordRatingChange(int before, int after) {
        this.ratingBefore = before;
        this.ratingAfter = after;
    }

    public Long getId() { return id; }
    public Match getMatch() { return match; }
    public User getUser() { return user; }
    public int getSeat() { return seat; }
    public MatchResult getResult() { return result; }
    public Integer getScore() { return score; }
    public Integer getRatingBefore() { return ratingBefore; }
    public Integer getRatingAfter() { return ratingAfter; }
    public Instant getJoinedAt() { return joinedAt; }
}
