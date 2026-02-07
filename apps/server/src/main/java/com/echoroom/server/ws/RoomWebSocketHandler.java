package com.echoroom.server.ws;

import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class RoomWebSocketHandler extends TextWebSocketHandler {

    private final RoomMessageRouter roomMessageRouter;

    public RoomWebSocketHandler(RoomMessageRouter roomMessageRouter) {
        this.roomMessageRouter = roomMessageRouter;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        roomMessageRouter.register(new SpringRoomSession(session));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        roomMessageRouter.handleMessage(session.getId(), message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        roomMessageRouter.handleClose(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        roomMessageRouter.handleClose(session.getId());
    }

    private static final class SpringRoomSession implements RoomSession {

        private final WebSocketSession delegate;

        private SpringRoomSession(WebSocketSession delegate) {
            this.delegate = delegate;
        }

        @Override
        public String id() {
            return delegate.getId();
        }

        @Override
        public boolean isOpen() {
            return delegate.isOpen();
        }

        @Override
        public void send(String text) {
            try {
                delegate.sendMessage(new TextMessage(text));
            } catch (IOException _error) {
                // Transport errors are handled by WebSocket callbacks.
            }
        }
    }
}
