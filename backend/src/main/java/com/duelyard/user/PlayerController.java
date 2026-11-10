package com.duelyard.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.duelyard.match.dto.MatchHistoryPage;
import com.duelyard.rating.dto.RatingHistoryResponse;
import com.duelyard.user.dto.PlayerProfileResponse;

/**
 * Public player profiles. Works signed out; when the caller is signed in (valid bearer token)
 * they can see their own private profile.
 */
@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final PlayerProfileService profiles;

    public PlayerController(PlayerProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/{username}")
    public PlayerProfileResponse profile(@PathVariable String username, @AuthenticationPrincipal Jwt viewer) {
        return profiles.profile(username, viewer);
    }

    @GetMapping("/{username}/matches")
    public MatchHistoryPage matches(
            @PathVariable String username,
            @AuthenticationPrincipal Jwt viewer,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return profiles.matches(username, viewer, page, size);
    }

    @GetMapping("/{username}/rating-history")
    public RatingHistoryResponse ratingHistory(
            @PathVariable String username,
            @AuthenticationPrincipal Jwt viewer,
            @RequestParam String game,
            @RequestParam(defaultValue = "100") int limit) {
        return profiles.ratingHistory(username, viewer, game, limit);
    }
}
