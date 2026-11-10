package com.duelyard.game;

import java.time.Instant;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Persistent record of a game, used as the foreign-key target for ratings and matches. Rows are
 * created by Flyway migrations, never at runtime; static metadata lives in {@link GameDefinition}.
 */
@Entity
@Immutable
@Table(name = "games")
public class Game {

    @Id
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = 32)
    private String slug;

    @Column(name = "display_name", nullable = false, length = 64)
    private String displayName;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected Game() {
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getDisplayName() { return displayName; }
    public Instant getCreatedAt() { return createdAt; }
}
