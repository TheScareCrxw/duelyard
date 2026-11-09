package com.duelyard.rating;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerGameStatsRepository extends JpaRepository<PlayerGameStats, Long> {

    Optional<PlayerGameStats> findByUserIdAndGameId(Long userId, Long gameId);

    List<PlayerGameStats> findByUserId(Long userId);

    /** All of a player's per-game stats with the game loaded, in a stable order. */
    @Query("select s from PlayerGameStats s join fetch s.game g where s.user.id = :userId order by g.id")
    List<PlayerGameStats> findAllWithGameByUserId(@Param("userId") Long userId);
}
