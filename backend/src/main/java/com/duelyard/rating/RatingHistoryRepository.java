package com.duelyard.rating;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RatingHistoryRepository extends JpaRepository<RatingHistory, Long> {

    boolean existsByMatchId(UUID matchId);

    List<RatingHistory> findByUserIdAndGameIdOrderByCreatedAtDesc(Long userId, Long gameId);

    /** The most recent rating changes of a player in one game, newest first. */
    @Query("""
        select h from RatingHistory h
        where h.user.id = :userId and h.game.id = :gameId
        order by h.createdAt desc, h.id desc
        """)
    List<RatingHistory> findRecent(@Param("userId") Long userId, @Param("gameId") Long gameId, Pageable pageable);
}
