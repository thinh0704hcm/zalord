export type MessageCallback = (message: any) => void;

class WebSocketService {
  private ws: WebSocket | null = null;
  private url = import.meta.env.VITE_WS_URL ?? 'ws://localhost:8080/ws/chat';
  private messageCallbacks: MessageCallback[] = [];

  connect(token: string) {
    if (this.ws) return;

    // Standard WebSocket does not support passing headers directly in browser.
    // Usually, token is passed via query string if the gateway allows it,
    // or Kong needs to be configured to read from query params.
    // For this example, we'll append it to the URL:
    this.ws = new WebSocket(`${this.url}?token=${token}`);

    this.ws.onopen = () => {
      console.log('WebSocket connected');
    };

    this.ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        this.messageCallbacks.forEach(cb => cb(data));
      } catch (e) {
        console.error('Invalid WS message', event.data);
      }
    };

    this.ws.onclose = () => {
      console.log('WebSocket disconnected');
      this.ws = null;
      // Reconnect logic could be added here
    };

    this.ws.onerror = (err) => {
      console.error('WebSocket error:', err);
    };
  }

  disconnect() {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }

  onMessage(callback: MessageCallback) {
    this.messageCallbacks.push(callback);
    return () => {
      this.messageCallbacks = this.messageCallbacks.filter(cb => cb !== callback);
    };
  }

  send(data: any) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data));
    }
  }
}

export const wsService = new WebSocketService();
