import { ClientMessage, ServerMessage, isServerMessage } from "@echo-room/shared";

export interface SocketHandlers {
  onOpen: () => void;
  onClose: () => void;
  onError: (error: Event) => void;
  onMessage: (message: ServerMessage) => void;
}

export class SocketClient {
  private socket?: WebSocket;

  public connect(url: string, handlers: SocketHandlers): void {
    this.socket = new WebSocket(url);

    this.socket.onopen = () => handlers.onOpen();
    this.socket.onclose = () => handlers.onClose();
    this.socket.onerror = (event) => handlers.onError(event);
    this.socket.onmessage = (event) => {
      try {
        const parsed: unknown = JSON.parse(event.data);
        if (isServerMessage(parsed)) {
          handlers.onMessage(parsed);
        }
      } catch (_error) {
        // Ignore malformed message.
      }
    };
  }

  public send(message: ClientMessage): void {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      return;
    }
    this.socket.send(JSON.stringify(message));
  }

  public close(): void {
    if (!this.socket) {
      return;
    }
    this.socket.close();
    this.socket = undefined;
  }
}
