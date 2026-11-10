package com.duelyard.leaderboard;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.duelyard.leaderboard.dto.LeaderboardResponse;

/** Public leaderboards. A signed-in caller also gets their own row in {@code viewer}. */
@RestController
@RequestMapping("/api/leaderboards")
public class LeaderboardController {

    private final LeaderboardService leaderboards;

    public LeaderboardController(LeaderboardService leaderboards) {
        this.leaderboards = leaderboards;
    }

    /** {@code scope} is "global" or a game slug. */
    @GetMapping("/{scope}")
    public LeaderboardResponse leaderboard(
            @PathVariable String scope,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt viewer) {
        return leaderboards.leaderboard(scope, search, page, size, viewer);
    }
}
