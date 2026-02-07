import { SignalPayload } from "@echo-room/shared";
import { RTCProvider, RTCProviderEvents, SignalKind } from "./RTCProvider";

const defaultIceServers: RTCIceServer[] = [{ urls: "stun:stun.l.google.com:19302" }];

const resolveIceServers = (): RTCIceServer[] => {
  const raw = import.meta.env.VITE_ICE_SERVERS as string | undefined;
  if (!raw) {
    return defaultIceServers;
  }
  try {
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) {
      return parsed as RTCIceServer[];
    }
  } catch (_error) {
    return defaultIceServers;
  }
  return defaultIceServers;
};

export class WebRTCMeshProvider implements RTCProvider {
  private readonly peers = new Map<string, RTCPeerConnection>();

  private localStream?: MediaStream;

  private readonly iceServers: RTCIceServer[];

  constructor(private readonly events: RTCProviderEvents) {
    this.iceServers = resolveIceServers();
  }

  public async init(localStream: MediaStream): Promise<void> {
    this.localStream = localStream;
  }

  public async addPeer(peerUserId: string, shouldCreateOffer: boolean): Promise<void> {
    const connection = this.getOrCreatePeer(peerUserId);
    if (!shouldCreateOffer) {
      return;
    }

    const offer = await connection.createOffer();
    await connection.setLocalDescription(offer);
    this.events.onSignal(peerUserId, "signal_offer", {
      targetUserId: peerUserId,
      sdp: connection.localDescription ?? offer
    });
  }

  public async handleSignal(fromUserId: string, type: SignalKind, payload: SignalPayload): Promise<void> {
    const connection = this.getOrCreatePeer(fromUserId);

    if (type === "signal_offer" && payload.sdp) {
      await connection.setRemoteDescription(new RTCSessionDescription(payload.sdp));
      const answer = await connection.createAnswer();
      await connection.setLocalDescription(answer);
      this.events.onSignal(fromUserId, "signal_answer", {
        targetUserId: fromUserId,
        sdp: connection.localDescription ?? answer
      });
      return;
    }

    if (type === "signal_answer" && payload.sdp) {
      await connection.setRemoteDescription(new RTCSessionDescription(payload.sdp));
      return;
    }

    if (type === "signal_ice" && payload.candidate) {
      await connection.addIceCandidate(new RTCIceCandidate(payload.candidate));
    }
  }

  public removePeer(peerUserId: string): void {
    const connection = this.peers.get(peerUserId);
    if (!connection) {
      return;
    }

    connection.onicecandidate = null;
    connection.ontrack = null;
    connection.onconnectionstatechange = null;
    connection.close();
    this.peers.delete(peerUserId);
  }

  public setMuted(muted: boolean): void {
    if (!this.localStream) {
      return;
    }
    this.localStream.getAudioTracks().forEach((track) => {
      track.enabled = !muted;
    });
  }

  public dispose(): void {
    Array.from(this.peers.keys()).forEach((peerUserId) => this.removePeer(peerUserId));
  }

  private getOrCreatePeer(peerUserId: string): RTCPeerConnection {
    const existing = this.peers.get(peerUserId);
    if (existing) {
      return existing;
    }

    const connection = new RTCPeerConnection({ iceServers: this.iceServers });
    if (this.localStream) {
      this.localStream.getTracks().forEach((track) => {
        connection.addTrack(track, this.localStream as MediaStream);
      });
    }

    connection.onicecandidate = (event) => {
      if (!event.candidate) {
        return;
      }
      this.events.onSignal(peerUserId, "signal_ice", {
        targetUserId: peerUserId,
        candidate: event.candidate.toJSON()
      });
    };

    connection.ontrack = (event) => {
      const [stream] = event.streams;
      if (!stream) {
        return;
      }
      this.events.onRemoteTrack(peerUserId, stream);
    };

    connection.onconnectionstatechange = () => {
      const connected = connection.connectionState === "connected";
      this.events.onPeerState(peerUserId, connected);
    };

    this.peers.set(peerUserId, connection);
    return connection;
  }
}
