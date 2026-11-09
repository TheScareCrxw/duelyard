package com.duelyard.rating;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RatingHistoryRepository extends JpaRepository<RatingHistory, Long> {

    boolean existsByMatchId(UUID matchId);

    List<RatingHistory> findByUserIdAndGameIdOrderByCreatedAtDesc(Long userId, Long gameId);
}
