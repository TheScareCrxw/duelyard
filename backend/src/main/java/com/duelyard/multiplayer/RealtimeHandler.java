package com.duelyard.multiplayer;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.duelyard.matchmaking.MatchmakingService;
import com.duelyard.user.UserRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Parses client messages and routes them. The user comes from the handshake ticket, never from the message. */
@Component
class RealtimeHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RealtimeHandler.class);
    private static final int MAX_MESSAGE_CHARS = 4096;

    private final ConnectionRegistry connections;
    private final MatchRoomService rooms;
    private final MatchmakingService matchmaking;
    private final UserRepository users;
    private final JsonMapper json;

    RealtimeHandler(ConnectionRegistry connections, MatchRoomService rooms, @Lazy MatchmakingService matchmaking,
            UserRepository users, JsonMapper json) {
        this.connections = connections;
        this.rooms = rooms;
        this.matchmaking = matchmaking;
        this.users = users;
        this.json = json;
    }

    private static long userId(WebSocketSession session) {
        return (Long) session.getAttributes().get(TicketHandshakeInterceptor.USER_ID);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        long userId = userId(session);
        connections.register(userId, session);
        String username = users.findById(userId).map(u -> u.getUsername()).orElse("");
        connections.send(userId, new Messages.Welcome(username, rooms.activeMatch(userId).orElse(null)));
        rooms.onConnected(userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        long userId = userId(session);
        if (message.getPayloadLength() > MAX_MESSAGE_CHARS) {
            connections.send(userId, new Messages.ErrorMessage("MESSAGE_TOO_LARGE", "Message too large."));
            return;
        }
        JsonNode msg;
        try {
            msg = json.readTree(message.getPayload());
        } catch (JacksonException e) {
            connections.send(userId, new Messages.ErrorMessage("BAD_MESSAGE", "Messages must be JSON."));
            return;
        }
        String type = msg.path("type").asString("");
        try {
            switch (type) {
                case "queue.join" -> matchmaking.join(userId, msg.path("game").asString(null), msg.path("mode").asString(null));
                case "queue.leave" -> matchmaking.leave(userId, "CANCELLED");
                case "match.action" -> rooms.handleAction(userId, matchId(msg), msg.get("input"));
                case "match.sync" -> rooms.sync(userId, matchId(msg));
                case "ping" -> connections.send(userId, new Messages.Pong());
                default -> connections.send(userId, new Messages.ErrorMessage("UNKNOWN_TYPE", "Unknown message type."));
            }
        } catch (RuntimeException e) {
            log.error("Error handling '{}' from user {}", type, userId, e);
            connections.send(userId, new Messages.ErrorMessage("SERVER_ERROR", "Something went wrong."));
        }
    }

    private static UUID matchId(JsonNode msg) {
        try {
            return UUID.fromString(msg.path("matchId").asString(""));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        long userId = userId(session);
        // A connection replaced by a newer one (another tab) isn't a disconnect.
        if (connections.unregister(userId, session)) {
            matchmaking.leave(userId, "DISCONNECTED");
            rooms.onDisconnected(userId);
        }
    }
}
