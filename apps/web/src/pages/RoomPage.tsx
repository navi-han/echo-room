import {
  ClientMessage,
  ServerMessage,
  SignalPayload,
  UserMutedPayload,
  UserJoinedPayload,
  UserLeftPayload
} from "@echo-room/shared";
import { FormEvent, useEffect, useMemo, useReducer, useRef, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { RTCProvider, SignalKind } from "../rtc/RTCProvider";
import { WebRTCMeshProvider } from "../rtc/WebRTCMeshProvider";
import { initialRoomState, roomReducer } from "../state/roomReducer";
import { SocketClient } from "../ws/socketClient";

const USER_STORAGE_KEY = "echo-room-user-id";

const getOrCreateUserId = (): string => {
  const saved = window.localStorage.getItem(USER_STORAGE_KEY);
  if (saved) {
    return saved;
  }

  const nextId = typeof window.crypto.randomUUID === "function"
    ? window.crypto.randomUUID()
    : `u_${Math.random().toString(36).slice(2, 10)}`;

  window.localStorage.setItem(USER_STORAGE_KEY, nextId);
  return nextId;
};

const resolveWsUrl = (): string => {
  const envBaseUrl = (import.meta.env.VITE_WS_BASE_URL as string | undefined)?.trim();
  const fallback = window.location.hostname === "localhost"
    ? "http://localhost:8080"
    : window.location.origin;
  const base = envBaseUrl || fallback;

  if (base.startsWith("ws://") || base.startsWith("wss://")) {
    return `${base.replace(/\/$/, "")}/ws`;
  }

  if (base.startsWith("http://") || base.startsWith("https://")) {
    const wsBase = base.startsWith("https://")
      ? base.replace("https://", "wss://")
      : base.replace("http://", "ws://");
    return `${wsBase.replace(/\/$/, "")}/ws`;
  }

  return `ws://${base.replace(/\/$/, "")}/ws`;
};

const sendSignalMessage = (
  socket: SocketClient,
  type: SignalKind,
  targetUserId: string,
  payload: SignalPayload
): void => {
  socket.send({
    type,
    payload: {
      ...payload,
      targetUserId
    }
  } as ClientMessage);
};

export function RoomPage(): JSX.Element {
  const { roomId = "" } = useParams();
  const [searchParams] = useSearchParams();
  const displayName = useMemo(() => {
    const raw = searchParams.get("name")?.trim();
    return raw || "Anonymous";
  }, [searchParams]);
  const selfUserId = useMemo(() => getOrCreateUserId(), []);

  const [state, dispatch] = useReducer(roomReducer, initialRoomState);
  const [isMuted, setIsMuted] = useState(false);
  const [remoteStreams, setRemoteStreams] = useState<Record<string, MediaStream>>({});
  const [aiInput, setAiInput] = useState("");

  const socketRef = useRef<SocketClient>();
  const rtcRef = useRef<RTCProvider>();
  const localStreamRef = useRef<MediaStream>();

  useEffect(() => {
    let active = true;
    const socket = new SocketClient();
    const rtcProvider = new WebRTCMeshProvider({
      onSignal: (targetUserId, type, payload) => sendSignalMessage(socket, type, targetUserId, payload),
      onRemoteTrack: (userId, stream) => {
        setRemoteStreams((previous) => ({
          ...previous,
          [userId]: stream
        }));
      },
      onPeerState: (userId, connected) => {
        dispatch({
          type: "peer_state",
          payload: { userId, connected }
        });
      }
    });

    socketRef.current = socket;
    rtcRef.current = rtcProvider;

    const onSocketMessage = async (message: ServerMessage): Promise<void> => {
      switch (message.type) {
        case "room_snapshot": {
          dispatch({
            type: "set_snapshot",
            payload: {
              roomId: message.payload.roomId,
              selfUserId: message.payload.selfUserId,
              participants: message.payload.participants
            }
          });

          await Promise.all(
            message.payload.participants
              .filter((participant) => participant.userId !== message.payload.selfUserId)
              .map((participant) => rtcProvider.addPeer(participant.userId, false))
          );
          return;
        }
        case "user_joined": {
          const payload = message.payload as UserJoinedPayload;
          dispatch({ type: "user_joined", payload: payload.user });
          if (payload.user.userId !== selfUserId) {
            await rtcProvider.addPeer(payload.user.userId, true);
          }
          return;
        }
        case "user_left": {
          const payload = message.payload as UserLeftPayload;
          dispatch({ type: "user_left", payload });
          rtcProvider.removePeer(payload.userId);
          setRemoteStreams((previous) => {
            const next = { ...previous };
            delete next[payload.userId];
            return next;
          });
          return;
        }
        case "signal_offer":
        case "signal_answer":
        case "signal_ice": {
          const payload = message.payload as SignalPayload;
          if (!payload.fromUserId) {
            return;
          }
          await rtcProvider.handleSignal(payload.fromUserId, message.type, payload);
          return;
        }
        case "user_muted": {
          const payload = message.payload as UserMutedPayload;
          dispatch({
            type: "user_muted",
            payload: {
              userId: payload.userId,
              muted: payload.muted
            }
          });
          return;
        }
        case "ai_reply":
          dispatch({ type: "ai_reply", payload: { text: message.payload.text } });
          return;
        case "error":
          dispatch({ type: "set_error", payload: message.payload.message });
          return;
      }
    };

    const start = async (): Promise<void> => {
      dispatch({ type: "set_connection_status", payload: "connecting" });
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          audio: true,
          video: false
        });

        if (!active) {
          stream.getTracks().forEach((track) => track.stop());
          return;
        }

        localStreamRef.current = stream;
        await rtcProvider.init(stream);

        socket.connect(resolveWsUrl(), {
          onOpen: () => {
            dispatch({ type: "set_connection_status", payload: "connected" });
            dispatch({ type: "set_error", payload: undefined });
            socket.send({
              type: "join_room",
              payload: {
                roomId,
                userId: selfUserId,
                displayName
              }
            });
          },
          onClose: () => {
            dispatch({ type: "set_connection_status", payload: "disconnected" });
          },
          onError: () => {
            dispatch({ type: "set_error", payload: "WebSocket connection error." });
          },
          onMessage: (message) => {
            void onSocketMessage(message);
          }
        });
      } catch (error) {
        dispatch({
          type: "set_error",
          payload: error instanceof Error ? error.message : "Failed to initialize audio stream."
        });
      }
    };

    void start();

    return () => {
      active = false;
      socket.send({
        type: "leave_room",
        payload: { roomId }
      });
      socket.close();
      rtcProvider.dispose();
      if (localStreamRef.current) {
        localStreamRef.current.getTracks().forEach((track) => track.stop());
      }
      setRemoteStreams({});
    };
  }, [displayName, roomId, selfUserId]);

  useEffect(() => {
    rtcRef.current?.setMuted(isMuted);
  }, [isMuted]);

  const toggleMuted = () => {
    const nextMuted = !isMuted;
    setIsMuted(nextMuted);
    rtcRef.current?.setMuted(nextMuted);
    socketRef.current?.send({
      type: "mute_state",
      payload: { muted: nextMuted }
    });
    dispatch({
      type: "user_muted",
      payload: {
        userId: selfUserId,
        muted: nextMuted
      }
    });
  };

  const onAiSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const text = aiInput.trim();
    if (!text) {
      return;
    }

    socketRef.current?.send({
      type: "ai_ping",
      payload: { text }
    });
    setAiInput("");
  };

  return (
    <main className="page">
      <section className="card wide">
        <div className="header-row">
          <div>
            <h1>Room: {roomId}</h1>
            <p>
              You are <strong>{displayName}</strong> ({selfUserId.slice(0, 8)})
            </p>
          </div>
          <Link to="/">Back</Link>
        </div>

        <div className="status-row">
          <span>Status: {state.connectionStatus}</span>
          <button onClick={toggleMuted}>
            {isMuted ? "Unmute Mic" : "Mute Mic"}
          </button>
        </div>

        {state.error ? <p className="error">{state.error}</p> : null}

        <h2>Participants ({Object.keys(state.participants).length}/5)</h2>
        <ul className="participant-list">
          {Object.values(state.participants).map((participant) => (
            <li key={participant.userId}>
              <span>{participant.displayName}</span>
              <span>
                {participant.isSelf ? "(You)" : participant.connected ? "Connected" : "Connecting"}
              </span>
              <span>{participant.muted ? "Muted" : "Speaking"}</span>
            </li>
          ))}
        </ul>

        <form onSubmit={onAiSubmit} className="ai-form">
          <input
            value={aiInput}
            onChange={(event) => setAiInput(event.target.value)}
            placeholder="Ping mock AI"
            maxLength={300}
          />
          <button type="submit">Send</button>
        </form>

        <ul className="ai-list">
          {state.aiReplies.map((reply) => (
            <li key={reply}>{reply}</li>
          ))}
        </ul>

        {Object.entries(remoteStreams).map(([userId, stream]) => (
          <audio
            key={userId}
            autoPlay
            playsInline
            ref={(node) => {
              if (!node || node.srcObject === stream) {
                return;
              }
              node.srcObject = stream;
            }}
          />
        ))}
      </section>
    </main>
  );
}
