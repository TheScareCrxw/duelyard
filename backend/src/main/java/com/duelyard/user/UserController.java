package com.duelyard.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.duelyard.user.dto.MeResponse;
import com.duelyard.user.dto.UpdateProfileRequest;
import com.duelyard.user.dto.UsernameAvailabilityResponse;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final ProfileSettingsService settings;
    private final UserRepository users;

    public UserController(ProfileSettingsService settings, UserRepository users) {
        this.settings = settings;
        this.users = users;
    }

    /** The caller's account, created on first call after sign-in. */
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return settings.me(jwt);
    }

    /** Updates the caller's username, avatar and/or profile visibility. */
    @PatchMapping("/me")
    public MeResponse update(@AuthenticationPrincipal Jwt jwt, @RequestBody UpdateProfileRequest request) {
        return settings.update(jwt, request);
    }

    /** Public: lets the registration form check a username before creating the Cognito account. */
    @GetMapping("/username-availability")
    public UsernameAvailabilityResponse usernameAvailability(@RequestParam String username) {
        if (!UsernamePolicy.isValid(username)) {
            return new UsernameAvailabilityResponse(username, false, "INVALID_FORMAT");
        }
        if (UsernamePolicy.isReserved(username)) {
            return new UsernameAvailabilityResponse(username, false, "RESERVED");
        }
        if (users.existsByUsernameIgnoreCase(username)) {
            return new UsernameAvailabilityResponse(username, false, "TAKEN");
        }
        return new UsernameAvailabilityResponse(username, true, null);
    }
}
