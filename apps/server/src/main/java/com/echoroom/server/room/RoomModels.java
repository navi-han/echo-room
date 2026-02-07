package com.echoroom.server.room;

import java.util.List;

public final class RoomModels {

    private RoomModels() {
    }

    public record Participant(String userId, String displayName, boolean muted) {
    }

    public record ParticipantSession(String roomId, String sessionId, Participant participant) {
    }

    public record RoomSnapshot(String roomId, List<Participant> participants) {
    }

    public record JoinResult(
        boolean accepted,
        String errorCode,
        String errorMessage,
        RoomSnapshot snapshot,
        ParticipantSession self
    ) {
        public static JoinResult rejected(String code, String message) {
            return new JoinResult(false, code, message, null, null);
        }

        public static JoinResult accepted(RoomSnapshot snapshot, ParticipantSession self) {
            return new JoinResult(true, null, null, snapshot, self);
        }
    }

    public record LeaveResult(boolean left, String roomId, Participant participant) {

        public static LeaveResult noop() {
            return new LeaveResult(false, null, null);
        }

        public static LeaveResult left(String roomId, Participant participant) {
            return new LeaveResult(true, roomId, participant);
        }
    }
}
