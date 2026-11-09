package com.duelyard.multiplayer;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Real-time tuning, bound from {@code app.realtime.*}.
 *
 * @param ticketTtl              how long a WebSocket ticket stays valid
 * @param countdown              pause between pairing and the first move
 * @param disconnectGrace        time a disconnected player has to come back before forfeiting
 * @param maxConsecutiveTimeouts timeouts in a row that forfeit the match
 * @param endedRoomRetention     how long a finished room stays queryable for late syncs
 * @param turnLimitOverride      if set, replaces every game's turn limit (tests, load tests)
 */
@ConfigurationProperties(prefix = "app.realtime")
public record RealtimeProperties(
        Duration ticketTtl,
        Duration countdown,
        Duration disconnectGrace,
        int maxConsecutiveTimeouts,
        Duration endedRoomRetention,
        Duration turnLimitOverride) {

    public RealtimeProperties {
        if (ticketTtl == null) ticketTtl = Duration.ofSeconds(30);
        if (countdown == null) countdown = Duration.ofSeconds(5);
        if (disconnectGrace == null) disconnectGrace = Duration.ofSeconds(30);
        if (maxConsecutiveTimeouts <= 0) maxConsecutiveTimeouts = 3;
        if (endedRoomRetention == null) endedRoomRetention = Duration.ofMinutes(2);
    }
}
