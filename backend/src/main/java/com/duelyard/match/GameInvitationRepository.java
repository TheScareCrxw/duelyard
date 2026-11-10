package com.duelyard.match;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GameInvitationRepository extends JpaRepository<GameInvitation, UUID> {
}
