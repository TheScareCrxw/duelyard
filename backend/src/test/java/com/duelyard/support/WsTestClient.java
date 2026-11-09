package com.duelyard.support;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A minimal player for integration tests: connects with a ticket and records every message it receives. */
public final class WsTestClient implements AutoCloseable {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final WebSocketSession session;
    private final BlockingQueue<JsonNode> source;

    private WsTestClient(WebSocketSession session, BlockingQueue<JsonNode> inbox) {
        this.session = session;
        this.source = inbox;
    }

    public static WsTestClient connect(int port, String ticket) throws Exception {
        BlockingQueue<JsonNode> inbox = new LinkedBlockingQueue<>();
        TextWebSocketHandler handler = new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                inbox.add(JSON.readTree(message.getPayload()));
            }
        };
        WebSocketSession session = new StandardWebSocketClient()
            .execute(handler, null, URI.create("ws://localhost:" + port + "/ws?ticket=" + ticket))
            .get(5, TimeUnit.SECONDS);
        return new WsTestClient(session, inbox);
    }

    public void send(Object message) throws Exception {
        session.sendMessage(new TextMessage(JSON.writeValueAsString(message)));
    }

    /** Next message of the given type, skipping others. Fails after {@code timeout}. */
    public JsonNode await(String type, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            JsonNode msg = source.poll(Math.max(1, (deadline - System.nanoTime()) / 1_000_000), TimeUnit.MILLISECONDS);
            if (msg != null && type.equals(msg.path("type").asString())) return msg;
        }
        throw new AssertionError("No '" + type + "' message within " + timeout);
    }

    /** Next message of any type, or null after {@code timeout}. */
    public JsonNode poll(Duration timeout) throws InterruptedException {
        return source.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public boolean isOpen() {
        return session.isOpen();
    }

    @Override
    public void close() throws Exception {
        if (session.isOpen()) session.close(CloseStatus.NORMAL);
    }
}
