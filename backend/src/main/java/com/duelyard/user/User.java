package com.duelyard.user;

import java.time.Instant;
import java.util.Objects;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * An application account. Identity is owned by Cognito; {@code cognitoSub} links the two and is
 * unique, so one Cognito identity can never map to more than one account. No credentials are
 * stored here.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cognito_sub", nullable = false, updatable = false, length = 128)
    private String cognitoSub;

    @Column(nullable = false, length = 20)
    private String username;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "avatar_url", length = 1024)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_visibility", nullable = false, length = 16)
    private ProfileVisibility profileVisibility = ProfileVisibility.PUBLIC;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected User() {
    }

    public User(String cognitoSub, String username, String email) {
        this.cognitoSub = Objects.requireNonNull(cognitoSub, "cognitoSub");
        this.username = Objects.requireNonNull(username, "username");
        this.email = Objects.requireNonNull(email, "email");
    }

    public Long getId() { return id; }
    public String getCognitoSub() { return cognitoSub; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getAvatarUrl() { return avatarUrl; }
    public ProfileVisibility getProfileVisibility() { return profileVisibility; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void changeUsername(String username) {
        this.username = Objects.requireNonNull(username, "username");
    }

    public void changeEmail(String email) {
        this.email = Objects.requireNonNull(email, "email");
    }

    public void changeAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public void changeProfileVisibility(ProfileVisibility visibility) {
        this.profileVisibility = Objects.requireNonNull(visibility, "visibility");
    }
}
