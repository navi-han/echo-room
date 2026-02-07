import { ParticipantPayload } from "@echo-room/shared";

export type ConnectionStatus = "idle" | "connecting" | "connected" | "disconnected";

export interface RoomParticipant extends ParticipantPayload {
  isSelf: boolean;
  connected: boolean;
}

export interface RoomState {
  connectionStatus: ConnectionStatus;
  roomId: string;
  selfUserId: string;
  participants: Record<string, RoomParticipant>;
  error?: string;
  aiReplies: string[];
}

export type RoomAction =
  | { type: "set_connection_status"; payload: ConnectionStatus }
  | { type: "set_error"; payload?: string }
  | {
      type: "set_snapshot";
      payload: {
        roomId: string;
        selfUserId: string;
        participants: ParticipantPayload[];
      };
    }
  | { type: "user_joined"; payload: ParticipantPayload }
  | { type: "user_left"; payload: { userId: string } }
  | { type: "user_muted"; payload: { userId: string; muted: boolean } }
  | { type: "peer_state"; payload: { userId: string; connected: boolean } }
  | { type: "ai_reply"; payload: { text: string } };

export const initialRoomState: RoomState = {
  connectionStatus: "idle",
  roomId: "",
  selfUserId: "",
  participants: {},
  aiReplies: []
};

const toParticipantMap = (
  participants: ParticipantPayload[],
  selfUserId: string
): Record<string, RoomParticipant> => {
  const entries = participants.map((participant) => [
    participant.userId,
    {
      ...participant,
      isSelf: participant.userId === selfUserId,
      connected: participant.userId === selfUserId
    }
  ] as const);

  return Object.fromEntries(entries);
};

export const roomReducer = (state: RoomState, action: RoomAction): RoomState => {
  switch (action.type) {
    case "set_connection_status":
      return {
        ...state,
        connectionStatus: action.payload
      };
    case "set_error":
      return {
        ...state,
        error: action.payload
      };
    case "set_snapshot":
      return {
        ...state,
        roomId: action.payload.roomId,
        selfUserId: action.payload.selfUserId,
        participants: toParticipantMap(action.payload.participants, action.payload.selfUserId)
      };
    case "user_joined": {
      const participant = action.payload;
      return {
        ...state,
        participants: {
          ...state.participants,
          [participant.userId]: {
            ...participant,
            isSelf: participant.userId === state.selfUserId,
            connected: participant.userId === state.selfUserId
          }
        }
      };
    }
    case "user_left": {
      const nextParticipants = { ...state.participants };
      delete nextParticipants[action.payload.userId];
      return {
        ...state,
        participants: nextParticipants
      };
    }
    case "user_muted": {
      const target = state.participants[action.payload.userId];
      if (!target) {
        return state;
      }
      return {
        ...state,
        participants: {
          ...state.participants,
          [action.payload.userId]: {
            ...target,
            muted: action.payload.muted
          }
        }
      };
    }
    case "peer_state": {
      const target = state.participants[action.payload.userId];
      if (!target) {
        return state;
      }
      return {
        ...state,
        participants: {
          ...state.participants,
          [action.payload.userId]: {
            ...target,
            connected: action.payload.connected
          }
        }
      };
    }
    case "ai_reply":
      return {
        ...state,
        aiReplies: [action.payload.text, ...state.aiReplies].slice(0, 6)
      };
    default:
      return state;
  }
};
