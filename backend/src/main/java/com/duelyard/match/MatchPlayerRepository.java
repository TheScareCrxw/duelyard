package com.duelyard.match;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchPlayerRepository extends JpaRepository<MatchPlayer, Long> {

    /** A player's finished matches, newest first, with match and game loaded. */
    @Query("""
        select mp from MatchPlayer mp
          join fetch mp.match m
          join fetch m.game
        where mp.user.id = :userId and m.status in :statuses
        order by m.endedAt desc, m.id desc
        """)
    Slice<MatchPlayer> findHistory(
        @Param("userId") Long userId, @Param("statuses") Collection<MatchStatus> statuses, Pageable pageable);

    /** The other participants of the given matches, with their user loaded. */
    @Query("""
        select mp from MatchPlayer mp
          join fetch mp.user
        where mp.match.id in :matchIds and mp.user.id <> :userId
        order by mp.seat
        """)
    List<MatchPlayer> findOpponents(@Param("matchIds") Collection<UUID> matchIds, @Param("userId") Long userId);
}
