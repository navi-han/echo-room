export type ClientMessageType =
  | "join_room"
  | "leave_room"
  | "signal_offer"
  | "signal_answer"
  | "signal_ice"
  | "mute_state"
  | "ai_ping";

export type ServerMessageType =
  | "room_snapshot"
  | "user_joined"
  | "user_left"
  | "signal_offer"
  | "signal_answer"
  | "signal_ice"
  | "user_muted"
  | "ai_reply"
  | "error";

export interface WsMessage<T extends string, P> {
  type: T;
  payload: P;
}

export interface ParticipantPayload {
  userId: string;
  displayName: string;
  muted: boolean;
}

export interface JoinRoomPayload {
  roomId: string;
  userId: string;
  displayName: string;
}

export interface LeaveRoomPayload {
  roomId: string;
}

export interface SignalPayload {
  targetUserId: string;
  fromUserId?: string;
  sdp?: RTCSessionDescriptionInit;
  candidate?: RTCIceCandidateInit;
}

export interface MuteStatePayload {
  muted: boolean;
}

export interface AiPingPayload {
  text: string;
}

export interface RoomSnapshotPayload {
  roomId: string;
  selfUserId: string;
  participants: ParticipantPayload[];
}

export interface UserJoinedPayload {
  roomId: string;
  user: ParticipantPayload;
}

export interface UserLeftPayload {
  roomId: string;
  userId: string;
}

export interface UserMutedPayload {
  roomId: string;
  userId: string;
  muted: boolean;
}

export interface AiReplyPayload {
  roomId: string;
  text: string;
}

export interface ErrorPayload {
  code: string;
  message: string;
}

export type ClientMessage =
  | WsMessage<"join_room", JoinRoomPayload>
  | WsMessage<"leave_room", LeaveRoomPayload>
  | WsMessage<"signal_offer", SignalPayload>
  | WsMessage<"signal_answer", SignalPayload>
  | WsMessage<"signal_ice", SignalPayload>
  | WsMessage<"mute_state", MuteStatePayload>
  | WsMessage<"ai_ping", AiPingPayload>;

export type ServerMessage =
  | WsMessage<"room_snapshot", RoomSnapshotPayload>
  | WsMessage<"user_joined", UserJoinedPayload>
  | WsMessage<"user_left", UserLeftPayload>
  | WsMessage<"signal_offer", SignalPayload>
  | WsMessage<"signal_answer", SignalPayload>
  | WsMessage<"signal_ice", SignalPayload>
  | WsMessage<"user_muted", UserMutedPayload>
  | WsMessage<"ai_reply", AiReplyPayload>
  | WsMessage<"error", ErrorPayload>;

export const isServerMessage = (value: unknown): value is ServerMessage => {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const maybeMessage = value as Partial<ServerMessage>;
  return typeof maybeMessage.type === "string" && "payload" in maybeMessage;
};
