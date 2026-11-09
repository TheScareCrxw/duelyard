package com.duelyard.multiplayer;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import tools.jackson.databind.json.JsonMapper;

/**
 * The live connection of each user. One connection per user: a new one (e.g. another tab)
 * replaces and closes the old. Sends are thread-safe and never block on a slow client.
 */
@Component
public class ConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConnectionRegistry.class);
    static final CloseStatus REPLACED = new CloseStatus(4000, "Connected elsewhere");

    private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final JsonMapper json;

    public ConnectionRegistry(JsonMapper json) {
        this.json = json;
    }

    void register(long userId, WebSocketSession raw) {
        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(raw, 5_000, 512 * 1024);
        WebSocketSession previous = sessions.put(userId, session);
        if (previous != null && previous.isOpen()) {
            try {
                previous.close(REPLACED);
            } catch (IOException ignored) {
                // Already gone.
            }
        }
    }

    /** Removes the user's connection if it is still this one. Returns true if it was. */
    boolean unregister(long userId, WebSocketSession raw) {
        WebSocketSession current = sessions.get(userId);
        if (current instanceof ConcurrentWebSocketSessionDecorator d && d.getDelegate() == raw) {
            return sessions.remove(userId, current);
        }
        return false;
    }

    public boolean isConnected(long userId) {
        WebSocketSession s = sessions.get(userId);
        return s != null && s.isOpen();
    }

    /** Sends a message (any record with a {@code type}) if the user is connected. */
    public void send(long userId, Object message) {
        WebSocketSession session = sessions.get(userId);
        if (session == null || !session.isOpen()) return;
        try {
            session.sendMessage(new TextMessage(json.writeValueAsString(message)));
        } catch (Exception e) {
            log.debug("Send to user {} failed: {}", userId, e.getMessage());
        }
    }
}
