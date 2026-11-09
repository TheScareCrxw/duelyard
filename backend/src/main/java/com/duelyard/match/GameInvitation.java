package com.duelyard.match;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import com.duelyard.game.Game;
import com.duelyard.game.GameMode;
import com.duelyard.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A direct challenge from one player to another (future feature; schema only for now). */
@Entity
@Table(name = "game_invitations")
public class GameInvitation {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false, updatable = false)
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inviter_id", nullable = false, updatable = false)
    private User inviter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invitee_id", nullable = false, updatable = false)
    private User invitee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private GameMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InvitationStatus status = InvitationStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private Match match;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    protected GameInvitation() {
    }

    public GameInvitation(Game game, User inviter, User invitee, GameMode mode, Instant expiresAt) {
        this.game = Objects.requireNonNull(game, "game");
        this.inviter = Objects.requireNonNull(inviter, "inviter");
        this.invitee = Objects.requireNonNull(invitee, "invitee");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public UUID getId() { return id; }
    public Game getGame() { return game; }
    public User getInviter() { return inviter; }
    public User getInvitee() { return invitee; }
    public GameMode getMode() { return mode; }
    public InvitationStatus getStatus() { return status; }
    public Match getMatch() { return match; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRespondedAt() { return respondedAt; }
}
