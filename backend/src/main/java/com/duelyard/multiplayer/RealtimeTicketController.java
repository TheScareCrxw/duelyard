package com.duelyard.multiplayer;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.duelyard.user.UserProvisioningService;

@RestController
@RequestMapping("/api/realtime")
public class RealtimeTicketController {

    public record TicketResponse(String ticket, long expiresInSeconds) {
    }

    private final TicketService tickets;
    private final UserProvisioningService users;
    private final RealtimeProperties properties;

    public RealtimeTicketController(TicketService tickets, UserProvisioningService users, RealtimeProperties properties) {
        this.tickets = tickets;
        this.users = users;
        this.properties = properties;
    }

    /** Exchanges the caller's access token for a one-time WebSocket ticket. */
    @PostMapping("/tickets")
    public TicketResponse issue(@AuthenticationPrincipal Jwt jwt) {
        long userId = users.getOrProvision(jwt).getId();
        return new TicketResponse(tickets.issue(userId), properties.ticketTtl().toSeconds());
    }
}
