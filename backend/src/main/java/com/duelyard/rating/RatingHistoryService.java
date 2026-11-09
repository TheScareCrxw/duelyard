package com.duelyard.rating;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duelyard.rating.dto.RatingHistoryResponse;

/** Read side of rating history. Access control is the caller's responsibility. */
@Service
public class RatingHistoryService {

    public static final int MAX_POINTS = 200;

    private final RatingHistoryRepository history;

    public RatingHistoryService(RatingHistoryRepository history) {
        this.history = history;
    }

    @Transactional(readOnly = true)
    public RatingHistoryResponse history(Long userId, Long gameId, String gameSlug, int limit) {
        int size = Math.clamp(limit, 1, MAX_POINTS);
        List<RatingHistory> newestFirst = history.findRecent(userId, gameId, PageRequest.of(0, size));
        List<RatingHistory> oldestFirst = newestFirst.reversed();

        int initial = oldestFirst.isEmpty() ? PlayerGameStats.INITIAL_RATING : oldestFirst.getFirst().getRatingBefore();
        List<RatingHistoryResponse.Point> points = oldestFirst.stream()
            .map(h -> new RatingHistoryResponse.Point(h.getMatch().getId(), h.getCreatedAt(), h.getRatingAfter(), h.getDelta()))
            .toList();
        return new RatingHistoryResponse(gameSlug, initial, points);
    }
}
