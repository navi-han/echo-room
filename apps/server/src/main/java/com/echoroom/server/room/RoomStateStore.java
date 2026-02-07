package com.echoroom.server.room;

import com.echoroom.server.room.RoomModels.JoinResult;
import com.echoroom.server.room.RoomModels.LeaveResult;
import com.echoroom.server.room.RoomModels.ParticipantSession;
import com.echoroom.server.room.RoomModels.RoomSnapshot;
import java.util.Optional;

public interface RoomStateStore {

    JoinResult join(String roomId, String sessionId, String userId, String displayName);

    LeaveResult leaveBySession(String sessionId);

    Optional<RoomSnapshot> getSnapshot(String roomId);

    Optional<ParticipantSession> findBySession(String sessionId);
}
