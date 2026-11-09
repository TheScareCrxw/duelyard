package com.duelyard.rating;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerGameStatsRepository extends JpaRepository<PlayerGameStats, Long> {

    Optional<PlayerGameStats> findByUserIdAndGameId(Long userId, Long gameId);

    List<PlayerGameStats> findByUserId(Long userId);
}
