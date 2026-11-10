package com.duelyard.multiplayer;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * One-time WebSocket tickets. Browsers can't attach an Authorization header to a WebSocket, and
 * access tokens live in HttpOnly cookies on the Next.js origin, so the Next.js server exchanges the
 * user's token for a short-lived random ticket that the browser presents when connecting.
 */
@Service
public class TicketService {

    private record Ticket(long userId, Instant expiresAt) {
    }

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final RealtimeProperties properties;
    private final Clock clock;

    public TicketService(RealtimeProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public String issue(long userId) {
        purgeExpired();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(ticket, new Ticket(userId, clock.instant().plus(properties.ticketTtl())));
        return ticket;
    }

    /** The ticket's user, if it exists and hasn't expired. A ticket works once. */
    public Optional<Long> redeem(String ticket) {
        if (ticket == null) return Optional.empty();
        Ticket t = tickets.remove(ticket);
        if (t == null || t.expiresAt().isBefore(clock.instant())) return Optional.empty();
        return Optional.of(t.userId());
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        tickets.values().removeIf(t -> t.expiresAt().isBefore(now));
    }
}
