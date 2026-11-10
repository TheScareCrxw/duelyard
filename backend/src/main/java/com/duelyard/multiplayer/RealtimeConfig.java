package com.duelyard.multiplayer;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.duelyard.config.CorsProperties;

/** WebSocket endpoint: {@code /ws?ticket=...}, browser origins limited to the configured frontend. */
@Configuration
@EnableWebSocket
class RealtimeConfig implements WebSocketConfigurer {

    private final RealtimeHandler handler;
    private final TicketService tickets;
    private final CorsProperties cors;

    RealtimeConfig(RealtimeHandler handler, TicketService tickets, CorsProperties cors) {
        this.handler = handler;
        this.tickets = tickets;
        this.cors = cors;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws")
            .addInterceptors(new TicketHandshakeInterceptor(tickets))
            .setAllowedOrigins(cors.allowedOrigins().toArray(String[]::new));
    }
}
