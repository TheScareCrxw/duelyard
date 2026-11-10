package com.duelyard.rating;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface PlayerGameStatsRepository extends JpaRepository<PlayerGameStats, Long> {

    Optional<PlayerGameStats> findByUserIdAndGameId(Long userId, Long gameId);

    List<PlayerGameStats> findByUserId(Long userId);

    /** All of a player's per-game stats with the game loaded, in a stable order. */
    @Query("select s from PlayerGameStats s join fetch s.game g where s.user.id = :userId order by g.id")
    List<PlayerGameStats> findAllWithGameByUserId(@Param("userId") Long userId);

    /**
     * Row-locks the stats of the given players in one game. Locks are taken in id order so two
     * transactions touching the same players can't deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PlayerGameStats s where s.game.id = :gameId and s.user.id in :userIds order by s.id")
    List<PlayerGameStats> lockForUpdate(@Param("userIds") Collection<Long> userIds, @Param("gameId") Long gameId);
}
