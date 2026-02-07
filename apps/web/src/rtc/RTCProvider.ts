import { SignalPayload } from "@echo-room/shared";

export type SignalKind = "signal_offer" | "signal_answer" | "signal_ice";

export interface RTCProviderEvents {
  onSignal: (targetUserId: string, type: SignalKind, payload: SignalPayload) => void;
  onRemoteTrack: (userId: string, stream: MediaStream) => void;
  onPeerState: (userId: string, connected: boolean) => void;
}

export interface RTCProvider {
  init(localStream: MediaStream): Promise<void>;
  addPeer(peerUserId: string, shouldCreateOffer: boolean): Promise<void>;
  handleSignal(fromUserId: string, type: SignalKind, payload: SignalPayload): Promise<void>;
  removePeer(peerUserId: string): void;
  setMuted(muted: boolean): void;
  dispose(): void;
}
