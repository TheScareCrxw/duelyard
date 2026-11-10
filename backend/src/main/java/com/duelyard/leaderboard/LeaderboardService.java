package com.duelyard.leaderboard;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duelyard.common.error.ApiException;
import com.duelyard.common.error.NotFoundException;
import com.duelyard.game.Game;
import com.duelyard.game.GameRepository;
import com.duelyard.leaderboard.dto.LeaderboardResponse;
import com.duelyard.user.User;
import com.duelyard.user.UserRepository;

/**
 * Global and per-game leaderboards. Only players with at least {@link #MIN_RANKED_GAMES} ranked
 * games appear. The global rating is the average across all games, so it rewards all-round
 * skill rather than one game played many times.
 */
@Service
public class LeaderboardService {

    public static final String GLOBAL = "global";
    public static final int MIN_RANKED_GAMES = 1;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_SEARCH_LENGTH = 20;

    private final LeaderboardRepository boards;
    private final GameRepository games;
    private final UserRepository users;

    public LeaderboardService(LeaderboardRepository boards, GameRepository games, UserRepository users) {
        this.boards = boards;
        this.games = games;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public LeaderboardResponse leaderboard(String scope, String search, int page, int size, Jwt viewer) {
        Long gameId = null;
        String scopeName = "Overall";
        if (!GLOBAL.equals(scope)) {
            Game game = games.findBySlug(scope).orElseThrow(() -> new NotFoundException("Unknown leaderboard: " + scope));
            gameId = game.getId();
            scopeName = game.getDisplayName();
        }

        String term = normalize(search);
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        LeaderboardRepository.Page result = boards.page(gameId, MIN_RANKED_GAMES, term, safeSize, safePage * safeSize);

        LeaderboardResponse.Entry viewerRow = null;
        if (viewer != null) {
            Long finalGameId = gameId;
            Optional<User> self = users.findByCognitoSub(viewer.getSubject());
            viewerRow = self.flatMap(u -> boards.rowFor(finalGameId, MIN_RANKED_GAMES, u.getId()))
                .map(LeaderboardService::toEntry)
                .orElse(null);
        }

        return new LeaderboardResponse(scope, scopeName, term, safePage, safeSize, result.total(),
            result.rows().stream().map(LeaderboardService::toEntry).toList(), viewerRow);
    }

    private static String normalize(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String trimmed = search.strip();
        if (trimmed.length() > MAX_SEARCH_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SEARCH_TOO_LONG",
                "Search terms can be at most " + MAX_SEARCH_LENGTH + " characters.");
        }
        return trimmed;
    }

    private static LeaderboardResponse.Entry toEntry(LeaderboardRow r) {
        return new LeaderboardResponse.Entry(r.rank(), r.username(), r.avatarUrl(), r.rating(), r.wins(), r.losses(),
            r.draws(), r.gamesPlayed());
    }
}
