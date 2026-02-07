package com.echoroom.server.ws;

import com.echoroom.server.ai.AIService;
import com.echoroom.server.ai.AIService.AIReply;
import com.echoroom.server.ai.AIService.AIRequest;
import com.echoroom.server.room.RoomModels.JoinResult;
import com.echoroom.server.room.RoomModels.LeaveResult;
import com.echoroom.server.room.RoomModels.Participant;
import com.echoroom.server.room.RoomModels.ParticipantSession;
import com.echoroom.server.room.RoomModels.RoomSnapshot;
import com.echoroom.server.room.RoomStateStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class RoomMessageRouter {

    private final RoomStateStore roomStateStore;
    private final AIService aiService;
    private final ObjectMapper objectMapper;
    private final Map<String, RoomSession> sessions = new ConcurrentHashMap<>();

    public RoomMessageRouter(RoomStateStore roomStateStore, AIService aiService, ObjectMapper objectMapper) {
        this.roomStateStore = roomStateStore;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
    }

    public void register(RoomSession session) {
        sessions.put(session.id(), session);
    }

    public void handleMessage(String sessionId, String payload) {
        IncomingMessage message;
        try {
            message = objectMapper.readValue(payload, IncomingMessage.class);
        } catch (JsonProcessingException error) {
            sendError(sessionId, "INVALID_JSON", "Malformed message payload.");
            return;
        }

        if (message.type == null || message.type.isBlank()) {
            sendError(sessionId, "INVALID_TYPE", "Message type is required.");
            return;
        }

        switch (message.type) {
            case "join_room" -> handleJoin(sessionId, message.payload);
            case "leave_room" -> leaveAndBroadcast(sessionId);
            case "signal_offer", "signal_answer", "signal_ice" -> handleSignal(sessionId, message.type, message.payload);
            case "mute_state" -> handleMuteState(sessionId, message.payload);
            case "ai_ping" -> handleAiPing(sessionId, message.payload);
            default -> sendError(sessionId, "UNSUPPORTED_TYPE", "Unsupported message type: " + message.type);
        }
    }

    public void handleClose(String sessionId) {
        leaveAndBroadcast(sessionId);
        sessions.remove(sessionId);
    }

    private void handleJoin(String sessionId, JsonNode payload) {
        String roomId = text(payload, "roomId");
        String userId = text(payload, "userId");
        String displayName = text(payload, "displayName");

        JoinResult joinResult = roomStateStore.join(roomId, sessionId, userId, displayName);
        if (!joinResult.accepted()) {
            sendError(sessionId, joinResult.errorCode(), joinResult.errorMessage());
            return;
        }

        RoomSnapshot snapshot = joinResult.snapshot();
        ParticipantSession self = joinResult.self();

        send(sessionId, "room_snapshot", Map.of(
            "roomId", snapshot.roomId(),
            "selfUserId", self.participant().userId(),
            "participants", snapshot.participants()
        ));

        broadcastToRoomExcept(snapshot.roomId(), sessionId, "user_joined", Map.of(
            "roomId", snapshot.roomId(),
            "user", self.participant()
        ));
    }

    private void handleSignal(String sessionId, String type, JsonNode payload) {
        Optional<ParticipantSession> sender = roomStateStore.findBySession(sessionId);
        if (sender.isEmpty()) {
            sendError(sessionId, "NOT_IN_ROOM", "Join a room before signaling.");
            return;
        }

        String targetUserId = text(payload, "targetUserId");
        if (targetUserId == null || targetUserId.isBlank()) {
            sendError(sessionId, "TARGET_REQUIRED", "targetUserId is required.");
            return;
        }

        Optional<RoomSession> targetSession = findSessionByRoomAndUserId(sender.get().roomId(), targetUserId);
        if (targetSession.isEmpty()) {
            sendError(sessionId, "TARGET_NOT_FOUND", "Target user is not connected.");
            return;
        }

        Map<String, Object> forwardPayload = new LinkedHashMap<>();
        if (payload != null && payload.isObject()) {
            payload.fields().forEachRemaining(entry -> {
                if (!"targetUserId".equals(entry.getKey())) {
                    forwardPayload.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class));
                }
            });
        }

        forwardPayload.put("fromUserId", sender.get().participant().userId());
        send(targetSession.get().id(), type, forwardPayload);
    }

    private void handleMuteState(String sessionId, JsonNode payload) {
        Optional<ParticipantSession> sender = roomStateStore.findBySession(sessionId);
        if (sender.isEmpty()) {
            sendError(sessionId, "NOT_IN_ROOM", "Join a room before updating mute state.");
            return;
        }

        boolean muted = payload != null && payload.path("muted").asBoolean(false);
        ParticipantSession participantSession = sender.get();

        broadcastToRoomExcept(participantSession.roomId(), sessionId, "user_muted", Map.of(
            "roomId", participantSession.roomId(),
            "userId", participantSession.participant().userId(),
            "muted", muted
        ));
    }

    private void handleAiPing(String sessionId, JsonNode payload) {
        Optional<ParticipantSession> sender = roomStateStore.findBySession(sessionId);
        if (sender.isEmpty()) {
            sendError(sessionId, "NOT_IN_ROOM", "Join a room before using AI features.");
            return;
        }

        String text = text(payload, "text");
        AIReply reply = aiService.reply(new AIRequest(
            sender.get().roomId(),
            sender.get().participant().userId(),
            text == null ? "" : text
        ));

        send(sessionId, "ai_reply", Map.of(
            "roomId", sender.get().roomId(),
            "text", reply.text()
        ));
    }

    private void leaveAndBroadcast(String sessionId) {
        LeaveResult leaveResult = roomStateStore.leaveBySession(sessionId);
        if (!leaveResult.left()) {
            return;
        }

        Participant participant = leaveResult.participant();
        broadcastToRoomExcept(leaveResult.roomId(), sessionId, "user_left", Map.of(
            "roomId", leaveResult.roomId(),
            "userId", participant.userId()
        ));
    }

    private Optional<RoomSession> findSessionByRoomAndUserId(String roomId, String userId) {
        return sessions.values().stream()
            .filter(RoomSession::isOpen)
            .filter(session -> roomStateStore.findBySession(session.id())
                .map(participantSession -> participantSession.roomId().equals(roomId)
                    && participantSession.participant().userId().equals(userId))
                .orElse(false)
            )
            .findFirst();
    }

    private void broadcastToRoomExcept(String roomId, String excludedSessionId, String type, Map<String, Object> payload) {
        sessions.values().stream()
            .filter(RoomSession::isOpen)
            .filter(session -> !session.id().equals(excludedSessionId))
            .filter(session -> roomStateStore.findBySession(session.id())
                .map(participantSession -> participantSession.roomId().equals(roomId))
                .orElse(false)
            )
            .forEach(session -> send(session.id(), type, payload));
    }

    private void sendError(String sessionId, String code, String message) {
        send(sessionId, "error", Map.of(
            "code", code,
            "message", message
        ));
    }

    private void send(String sessionId, String type, Object payload) {
        RoomSession session = sessions.get(sessionId);
        if (session == null || !session.isOpen()) {
            return;
        }

        try {
            session.send(objectMapper.writeValueAsString(new OutgoingMessage(type, payload)));
        } catch (JsonProcessingException _error) {
            // Ignore serialization error to avoid breaking session loop.
        }
    }

    private String text(JsonNode payload, String field) {
        if (payload == null) {
            return null;
        }
        JsonNode node = payload.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private static final class IncomingMessage {
        public String type;
        public JsonNode payload;
    }

    private record OutgoingMessage(String type, Object payload) {
    }
}
