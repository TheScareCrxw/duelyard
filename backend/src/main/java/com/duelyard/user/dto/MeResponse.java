package com.duelyard.user.dto;

import java.time.Instant;

import com.duelyard.user.User;

/** The signed-in user's own account. Includes private fields (email), so only ever returned to its owner. */
public record MeResponse(
        Long id,
        String username,
        String email,
        String avatarUrl,
        String profileVisibility,
        Instant createdAt) {

    public static MeResponse from(User user) {
        return new MeResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getAvatarUrl(),
            user.getProfileVisibility().name(),
            user.getCreatedAt());
    }
}
