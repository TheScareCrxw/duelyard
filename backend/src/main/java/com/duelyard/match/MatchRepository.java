package com.duelyard.match;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface MatchRepository extends JpaRepository<Match, UUID> {

    /** Row-locks a match so only one transaction at a time can change its outcome. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Match m where m.id = :id")
    Optional<Match> lockById(@Param("id") UUID id);
}
