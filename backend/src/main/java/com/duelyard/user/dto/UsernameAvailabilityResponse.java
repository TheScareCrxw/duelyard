package com.duelyard.user.dto;

/** @param reason null when available; otherwise {@code INVALID_FORMAT} or {@code TAKEN} */
public record UsernameAvailabilityResponse(String username, boolean available, String reason) {
}
