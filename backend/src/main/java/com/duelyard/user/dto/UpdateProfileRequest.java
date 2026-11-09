package com.duelyard.user.dto;

import com.duelyard.user.ProfileVisibility;

/**
 * Partial update of the caller's profile. Null fields are left unchanged; an empty
 * {@code avatarUrl} removes the avatar.
 */
public record UpdateProfileRequest(String username, String avatarUrl, ProfileVisibility profileVisibility) {
}
