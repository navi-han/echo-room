package com.echoroom.server.room;

import com.echoroom.server.room.RoomModels.JoinResult;
import com.echoroom.server.room.RoomModels.LeaveResult;
import com.echoroom.server.room.RoomModels.Participant;
import com.echoroom.server.room.RoomModels.ParticipantSession;
import com.echoroom.server.room.RoomModels.RoomSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryRoomStateStore implements RoomStateStore {

    private static final int MAX_ROOM_CAPACITY = 5;

    private final Map<String, LinkedHashMap<String, ParticipantSession>> roomSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToRoom = new ConcurrentHashMap<>();

    @Override
    public synchronized JoinResult join(String roomId, String sessionId, String userId, String displayName) {
        if (roomId == null || roomId.isBlank() || userId == null || userId.isBlank()) {
            return JoinResult.rejected("INVALID_JOIN", "Room ID and User ID are required.");
        }

        leaveBySession(sessionId);

        LinkedHashMap<String, ParticipantSession> room = roomSessions.computeIfAbsent(roomId, _key -> new LinkedHashMap<>());
        if (room.size() >= MAX_ROOM_CAPACITY) {
            return JoinResult.rejected("ROOM_FULL", "Room is full (max 5 participants).");
        }

        Participant participant = new Participant(userId, displayName == null || displayName.isBlank() ? "Anonymous" : displayName, false);
        ParticipantSession participantSession = new ParticipantSession(roomId, sessionId, participant);

        room.put(sessionId, participantSession);
        sessionToRoom.put(sessionId, roomId);

        return JoinResult.accepted(snapshotFrom(roomId, room), participantSession);
    }

    @Override
    public synchronized LeaveResult leaveBySession(String sessionId) {
        String roomId = sessionToRoom.remove(sessionId);
        if (roomId == null) {
            return LeaveResult.noop();
        }

        LinkedHashMap<String, ParticipantSession> room = roomSessions.get(roomId);
        if (room == null) {
            return LeaveResult.noop();
        }

        ParticipantSession removed = room.remove(sessionId);
        if (room.isEmpty()) {
            roomSessions.remove(roomId);
        }

        if (removed == null) {
            return LeaveResult.noop();
        }

        return LeaveResult.left(roomId, removed.participant());
    }

    @Override
    public synchronized Optional<RoomSnapshot> getSnapshot(String roomId) {
        LinkedHashMap<String, ParticipantSession> room = roomSessions.get(roomId);
        if (room == null) {
            return Optional.empty();
        }
        return Optional.of(snapshotFrom(roomId, room));
    }

    @Override
    public synchronized Optional<ParticipantSession> findBySession(String sessionId) {
        String roomId = sessionToRoom.get(sessionId);
        if (roomId == null) {
            return Optional.empty();
        }

        LinkedHashMap<String, ParticipantSession> room = roomSessions.get(roomId);
        if (room == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(room.get(sessionId));
    }

    private RoomSnapshot snapshotFrom(String roomId, LinkedHashMap<String, ParticipantSession> room) {
        List<Participant> participants = new ArrayList<>();
        for (ParticipantSession participantSession : room.values()) {
            participants.add(participantSession.participant());
        }
        return new RoomSnapshot(roomId, participants);
    }
}
