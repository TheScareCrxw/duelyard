package com.duelyard.user;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duelyard.auth.CognitoAliasTakenException;
import com.duelyard.auth.CognitoUserInfoClient;
import com.duelyard.common.error.ApiException;
import com.duelyard.user.dto.MeResponse;
import com.duelyard.user.dto.UpdateProfileRequest;

/** Changes to the caller's own profile. Every method acts only on the authenticated user. */
@Service
public class ProfileSettingsService {

    /** Players may rename once per this period. */
    public static final Duration RENAME_COOLDOWN = Duration.ofDays(30);

    private final UserProvisioningService provisioning;
    private final UserRepository users;
    private final CognitoUserInfoClient cognito;
    private final Clock clock;

    public ProfileSettingsService(
            UserProvisioningService provisioning, UserRepository users, CognitoUserInfoClient cognito, Clock clock) {
        this.provisioning = provisioning;
        this.users = users;
        this.cognito = cognito;
        this.clock = clock;
    }

    public MeResponse me(Jwt jwt) {
        User user = provisioning.getOrProvision(jwt);
        return MeResponse.from(user, renameAvailableAt(user));
    }

    @Transactional
    public MeResponse update(Jwt jwt, UpdateProfileRequest request) {
        Long id = provisioning.getOrProvision(jwt).getId();
        User user = users.findById(id).orElseThrow();

        if (request.username() != null && !request.username().equals(user.getUsername())) {
            rename(user, request.username(), jwt.getTokenValue());
        }
        if (request.avatarUrl() != null) {
            changeAvatar(user, request.avatarUrl());
        }
        if (request.profileVisibility() != null) {
            user.changeProfileVisibility(request.profileVisibility());
        }
        users.flush();
        return MeResponse.from(user, renameAvailableAt(user));
    }

    private void rename(User user, String username, String accessToken) {
        if (!UsernamePolicy.isValid(username)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USERNAME_INVALID",
                "Usernames are 3-20 characters: letters, numbers and underscores.");
        }
        if (UsernamePolicy.isReserved(username)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USERNAME_RESERVED", "That username is reserved.");
        }
        Instant availableAt = renameAvailableAt(user);
        if (availableAt != null) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_CHANGE_TOO_SOON",
                "You can change your username once every 30 days.", Map.of("availableAt", availableAt.toString()));
        }
        boolean caseChangeOnly = username.equalsIgnoreCase(user.getUsername());
        if (!caseChangeOnly && users.existsByUsernameIgnoreCase(username)) {
            throw taken();
        }

        user.rename(username, clock.instant());
        try {
            users.flush();
        } catch (DataIntegrityViolationException e) {
            throw taken();
        }
        // Inside the transaction: if Cognito rejects the alias, the rename rolls back.
        try {
            cognito.setPreferredUsername(accessToken, username);
        } catch (CognitoAliasTakenException e) {
            throw taken();
        }
    }

    private static void changeAvatar(User user, String avatarUrl) {
        if (avatarUrl.isEmpty()) {
            user.changeAvatarUrl(null);
            return;
        }
        if (!AvatarCatalog.isValid(avatarUrl)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AVATAR_INVALID", "That avatar is not available.");
        }
        user.changeAvatarUrl(avatarUrl);
    }

    private Instant renameAvailableAt(User user) {
        if (user.getUsernameChangedAt() == null) {
            return null;
        }
        Instant next = user.getUsernameChangedAt().plus(RENAME_COOLDOWN);
        return next.isAfter(clock.instant()) ? next : null;
    }

    private static ApiException taken() {
        return new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "That username is already taken.");
    }
}
