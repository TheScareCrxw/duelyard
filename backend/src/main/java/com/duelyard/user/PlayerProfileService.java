package com.duelyard.user;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duelyard.common.error.ApiException;
import com.duelyard.common.error.NotFoundException;
import com.duelyard.game.Game;
import com.duelyard.game.GameRepository;
import com.duelyard.match.MatchHistoryService;
import com.duelyard.match.dto.MatchHistoryPage;
import com.duelyard.rating.PlayerGameStats;
import com.duelyard.rating.PlayerGameStatsRepository;
import com.duelyard.rating.RatingHistoryService;
import com.duelyard.rating.dto.RatingHistoryResponse;
import com.duelyard.rating.dto.GameRatingResponse;
import com.duelyard.user.dto.PlayerProfileResponse;

/**
 * Public player profiles. Private profiles show only identity basics to anyone but their owner.
 * Viewing never creates an account: the viewer is identified only by comparing token subjects.
 */
@Service
public class PlayerProfileService {

    public static final int RECENT_MATCHES = 10;

    private final UserRepository users;
    private final PlayerGameStatsRepository stats;
    private final MatchHistoryService history;
    private final RatingHistoryService ratingHistory;
    private final GameRepository games;

    public PlayerProfileService(UserRepository users, PlayerGameStatsRepository stats, MatchHistoryService history,
            RatingHistoryService ratingHistory, GameRepository games) {
        this.users = users;
        this.stats = stats;
        this.history = history;
        this.ratingHistory = ratingHistory;
        this.games = games;
    }

    @Transactional(readOnly = true)
    public PlayerProfileResponse profile(String username, Jwt viewer) {
        User user = find(username);
        boolean self = isSelf(user, viewer);
        boolean restricted = !self && user.getProfileVisibility() == ProfileVisibility.PRIVATE;

        if (restricted) {
            return new PlayerProfileResponse(user.getUsername(), user.getAvatarUrl(), user.getCreatedAt(),
                user.getProfileVisibility().name(), false, true, null, null, null);
        }

        List<PlayerGameStats> perGame = stats.findAllWithGameByUserId(user.getId());
        return new PlayerProfileResponse(
            user.getUsername(),
            user.getAvatarUrl(),
            user.getCreatedAt(),
            user.getProfileVisibility().name(),
            self,
            false,
            summarize(perGame),
            perGame.stream().map(GameRatingResponse::from).toList(),
            history.history(user.getId(), 0, RECENT_MATCHES).items());
    }

    @Transactional(readOnly = true)
    public MatchHistoryPage matches(String username, Jwt viewer, int page, int size) {
        User user = findVisible(username, viewer);
        return history.history(user.getId(), page, size);
    }

    @Transactional(readOnly = true)
    public RatingHistoryResponse ratingHistory(String username, Jwt viewer, String gameSlug, int limit) {
        User user = findVisible(username, viewer);
        Game game = games.findBySlug(gameSlug).orElseThrow(() -> new NotFoundException("Unknown game: " + gameSlug));
        return ratingHistory.history(user.getId(), game.getId(), game.getSlug(), limit);
    }

    /** The player, if the viewer may see their stats. */
    private User findVisible(String username, Jwt viewer) {
        User user = find(username);
        if (!isSelf(user, viewer) && user.getProfileVisibility() == ProfileVisibility.PRIVATE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PROFILE_PRIVATE", "This profile is private.");
        }
        return user;
    }

    private User find(String username) {
        return users.findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new NotFoundException("No player named " + username));
    }

    private static boolean isSelf(User user, Jwt viewer) {
        return viewer != null && user.getCognitoSub().equals(viewer.getSubject());
    }

    static PlayerProfileResponse.Summary summarize(List<PlayerGameStats> perGame) {
        int wins = 0;
        int losses = 0;
        int draws = 0;
        int highest = PlayerGameStats.INITIAL_RATING;
        for (PlayerGameStats s : perGame) {
            wins += s.getRankedWins() + s.getCasualWins();
            losses += s.getRankedLosses() + s.getCasualLosses();
            draws += s.getRankedDraws() + s.getCasualDraws();
            highest = Math.max(highest, s.getPeakRating());
        }
        int played = wins + losses + draws;
        Double winRate = played == 0 ? null : (double) wins / played;
        return new PlayerProfileResponse.Summary(played, wins, losses, draws, winRate, highest);
    }
}
