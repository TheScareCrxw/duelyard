package com.duelyard.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.duelyard.user.dto.MeResponse;
import com.duelyard.user.dto.UsernameAvailabilityResponse;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserProvisioningService provisioning;
    private final UserRepository users;

    public UserController(UserProvisioningService provisioning, UserRepository users) {
        this.provisioning = provisioning;
        this.users = users;
    }

    /** The caller's account, created on first call after sign-in. */
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return MeResponse.from(provisioning.getOrProvision(jwt));
    }

    /** Public: lets the registration form check a username before creating the Cognito account. */
    @GetMapping("/username-availability")
    public UsernameAvailabilityResponse usernameAvailability(@RequestParam String username) {
        if (!UsernamePolicy.isValid(username)) {
            return new UsernameAvailabilityResponse(username, false, "INVALID_FORMAT");
        }
        if (users.existsByUsernameIgnoreCase(username)) {
            return new UsernameAvailabilityResponse(username, false, "TAKEN");
        }
        return new UsernameAvailabilityResponse(username, true, null);
    }
}
