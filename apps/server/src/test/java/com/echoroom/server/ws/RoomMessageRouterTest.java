package com.echoroom.server.ws;

import static org.assertj.core.api.Assertions.assertThat;

import com.echoroom.server.ai.MockAIService;
import com.echoroom.server.room.InMemoryRoomStateStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoomMessageRouterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RoomMessageRouter router;

    @BeforeEach
    void setUp() {
        router = new RoomMessageRouter(new InMemoryRoomStateStore(), new MockAIService(), objectMapper);
    }

    @Test
    void shouldRelaySignalsAndBroadcastJoin() throws JsonProcessingException {
        TestRoomSession a = new TestRoomSession("s-a");
        TestRoomSession b = new TestRoomSession("s-b");
        router.register(a);
        router.register(b);

        send(a, "join_room", Map.of("roomId", "r-1", "userId", "u-a", "displayName", "A"));
        send(b, "join_room", Map.of("roomId", "r-1", "userId", "u-b", "displayName", "B"));

        assertThat(a.findType("user_joined")).isPresent();
        assertThat(a.findType("room_snapshot")).isPresent();
        assertThat(b.findType("room_snapshot")).isPresent();

        send(a, "signal_offer", Map.of(
            "targetUserId", "u-b",
            "sdp", Map.of("type", "offer", "sdp", "demo-offer")
        ));

        JsonNode bSignal = b.findType("signal_offer").orElseThrow();
        assertThat(bSignal.path("payload").path("fromUserId").asText()).isEqualTo("u-a");
        assertThat(bSignal.path("payload").path("sdp").path("sdp").asText()).isEqualTo("demo-offer");
    }

    @Test
    void shouldRejectSixthJoin() throws JsonProcessingException {
        for (int i = 1; i <= 5; i++) {
            TestRoomSession session = new TestRoomSession("s-" + i);
            router.register(session);
            send(session, "join_room", Map.of(
                "roomId", "r-full",
                "userId", "u-" + i,
                "displayName", "U" + i
            ));
        }

        TestRoomSession sixth = new TestRoomSession("s-6");
        router.register(sixth);
        send(sixth, "join_room", Map.of(
            "roomId", "r-full",
            "userId", "u-6",
            "displayName", "U6"
        ));

        JsonNode error = sixth.findType("error").orElseThrow();
        assertThat(error.path("payload").path("code").asText()).isEqualTo("ROOM_FULL");
    }

    @Test
    void shouldRespondToAiPing() throws JsonProcessingException {
        TestRoomSession session = new TestRoomSession("s-ai");
        router.register(session);

        send(session, "join_room", Map.of(
            "roomId", "r-ai",
            "userId", "u-ai",
            "displayName", "AI"
        ));

        send(session, "ai_ping", Map.of("text", "hello"));

        JsonNode reply = session.findType("ai_reply").orElseThrow();
        assertThat(reply.path("payload").path("text").asText()).contains("hello");
    }

    @Test
    void shouldBroadcastUserLeftOnClose() throws JsonProcessingException {
        TestRoomSession a = new TestRoomSession("s-a");
        TestRoomSession b = new TestRoomSession("s-b");
        router.register(a);
        router.register(b);

        send(a, "join_room", Map.of("roomId", "r-close", "userId", "u-a", "displayName", "A"));
        send(b, "join_room", Map.of("roomId", "r-close", "userId", "u-b", "displayName", "B"));

        router.handleClose("s-a");

        JsonNode userLeft = b.findType("user_left").orElseThrow();
        assertThat(userLeft.path("payload").path("userId").asText()).isEqualTo("u-a");
    }

    private void send(TestRoomSession session, String type, Map<String, Object> payload) throws JsonProcessingException {
        router.handleMessage(session.id(), objectMapper.writeValueAsString(Map.of(
            "type", type,
            "payload", payload
        )));
    }

    private final class TestRoomSession implements RoomSession {

        private final String id;
        private final List<JsonNode> outbound = new CopyOnWriteArrayList<>();

        private TestRoomSession(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public void send(String text) {
            try {
                outbound.add(objectMapper.readTree(text));
            } catch (JsonProcessingException _error) {
                // Ignore parse failures in tests.
            }
        }

        private java.util.Optional<JsonNode> findType(String type) {
            return outbound.stream().filter(node -> type.equals(node.path("type").asText())).reduce((first, second) -> second);
        }
    }
}
