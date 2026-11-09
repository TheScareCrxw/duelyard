package com.duelyard.match;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<Match, UUID> {
}
