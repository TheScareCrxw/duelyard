package com.duelyard.match;

import java.time.Instant;
import java.util.Objects;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 * One entry in a match's ordered event log (e.g. a validated throw or shot). {@code sequence} is
 * unique per match, which rejects duplicated or replayed events at the database level.
 * {@code payload} is game-specific JSON stored as JSONB.
 */
@Entity
@Immutable
@Table(name = "match_events")
public class MatchEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Column(nullable = false)
    private int sequence;

    /** The acting player, or null for system events (timeouts, match start). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MatchEvent() {
    }

    public MatchEvent(Match match, int sequence, User user, String eventType, String payloadJson) {
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be >= 0");
        }
        this.match = Objects.requireNonNull(match, "match");
        this.sequence = sequence;
        this.user = user;
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.payload = payloadJson == null ? "{}" : payloadJson;
    }

    public Long getId() { return id; }
    public Match getMatch() { return match; }
    public int getSequence() { return sequence; }
    public User getUser() { return user; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
}
