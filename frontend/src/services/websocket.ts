export type WebSocketFrame<TData = unknown> = {
  type: string;
  data?: TData;
};

export type MessageCreatedFrameData = {
  messageId: string;
  conversationId: string;
  senderId: string;
  content: string;
  createdAt?: string;
  attachmentIds?: string[];
};

export type TypingFrameData = {
  conversationId: string;
  isTyping: boolean;
  userId?: string;
};

export type IncomingWebSocketFrame = WebSocketFrame<unknown>;
export type MessageCreatedFrame = {
  type: 'message.created';
  data: MessageCreatedFrameData;
};

export type TypingFrame = {
  type: 'typing';
  data: TypingFrameData;
};

export type MessageCallback = (message: IncomingWebSocketFrame) => void;

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null;

export const isMessageCreatedFrame = (frame: IncomingWebSocketFrame): frame is MessageCreatedFrame => {
  if (frame.type !== 'message.created' || !isRecord(frame.data)) return false;
  return typeof frame.data.messageId === 'string'
    && typeof frame.data.conversationId === 'string'
    && typeof frame.data.senderId === 'string'
    && typeof frame.data.content === 'string';
};

export const isTypingFrame = (frame: IncomingWebSocketFrame): frame is TypingFrame => {
  if (frame.type !== 'typing' || !isRecord(frame.data)) return false;
  return typeof frame.data.conversationId === 'string'
    && typeof frame.data.isTyping === 'boolean';
};

class WebSocketService {
  private ws: WebSocket | null = null;
  private token: string | null = null;
  private url = import.meta.env.VITE_WS_URL ?? 'ws://localhost:8080/ws/chat';
  private messageCallbacks: MessageCallback[] = [];

  connect(token = localStorage.getItem('token')) {
    if (!token) return;
    if (this.ws && this.token === token) return;
    if (this.ws) {
      this.ws.close();
    }

    this.token = token;
    this.ws = new WebSocket(`${this.url}?token=${token}`);

    this.ws.onopen = () => {
      console.log('WebSocket connected');
    };

    this.ws.onmessage = (event) => {
      try {
        const data: unknown = JSON.parse(event.data);
        if (isRecord(data) && typeof data.type === 'string') {
          this.messageCallbacks.forEach(cb => cb(data as IncomingWebSocketFrame));
        }
      } catch {
        console.error('Invalid WS message', event.data);
      }
    };

    this.ws.onclose = () => {
      console.log('WebSocket disconnected');
      this.ws = null;
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
    this.token = null;
  }

  onMessage(callback: MessageCallback) {
    this.messageCallbacks.push(callback);
    return () => {
      this.messageCallbacks = this.messageCallbacks.filter(cb => cb !== callback);
    };
  }

  send(data: WebSocketFrame) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data));
    }
  }

  sendTyping(conversationId: string, isTyping: boolean) {
    this.send({
      type: 'typing',
      data: {
        conversationId,
        isTyping
      }
    });
  }
}

export const wsService = new WebSocketService();
