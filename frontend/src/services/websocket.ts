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

export type PresenceStatus = 'online' | 'offline';

export type PresenceStateFrameData = {
  states: Record<string, PresenceStatus>;
};

export type PresenceEventFrameData = {
  userId: string;
  status: PresenceStatus;
  at: string;
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

export type PresenceStateFrame = {
  type: 'presence.state';
  data: PresenceStateFrameData;
};

export type PresenceEventFrame = {
  type: 'presence';
  data: PresenceEventFrameData;
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

const isPresenceStatus = (value: unknown): value is PresenceStatus =>
  value === 'online' || value === 'offline';

export const isPresenceStateFrame = (frame: IncomingWebSocketFrame): frame is PresenceStateFrame => {
  if (frame.type !== 'presence.state' || !isRecord(frame.data) || !isRecord(frame.data.states)) return false;
  return Object.values(frame.data.states).every(isPresenceStatus);
};

export const isPresenceEventFrame = (frame: IncomingWebSocketFrame): frame is PresenceEventFrame => {
  if (frame.type !== 'presence' || !isRecord(frame.data)) return false;
  return typeof frame.data.userId === 'string'
    && isPresenceStatus(frame.data.status)
    && typeof frame.data.at === 'string';
};

class WebSocketService {
  private ws: WebSocket | null = null;
  private token: string | null = null;
  private url = import.meta.env.VITE_WS_URL ?? 'ws://localhost:8080/ws/chat';
  private messageCallbacks: MessageCallback[] = [];
  private openCallbacks: Array<() => void> = [];
  private pendingFrames: WebSocketFrame[] = [];
  private reconnectTimer: ReturnType<typeof window.setTimeout> | null = null;
  private reconnectAttempt = 0;
  private shouldReconnect = false;

  connect(token = localStorage.getItem('token')) {
    if (!token) return;
    if (this.ws && this.token === token && (
      this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING
    )) return;
    if (this.ws) {
      this.ws.onclose = null;
      this.ws.close();
    }

    this.clearReconnectTimer();
    this.shouldReconnect = true;
    this.token = token;
    const socket = new WebSocket(`${this.url}?token=${token}`);
    this.ws = socket;

    socket.onopen = () => {
      console.log('WebSocket connected');
      this.reconnectAttempt = 0;
      const queuedFrames = this.pendingFrames.splice(0);
      queuedFrames.forEach(frame => this.send(frame));
      this.openCallbacks.forEach(cb => cb());
    };

    socket.onmessage = (event) => {
      try {
        const data: unknown = JSON.parse(event.data);
        if (isRecord(data) && typeof data.type === 'string') {
          this.messageCallbacks.forEach(cb => cb(data as IncomingWebSocketFrame));
        }
      } catch {
        console.error('Invalid WS message', event.data);
      }
    };

    socket.onclose = () => {
      if (this.ws !== socket) return;
      console.log('WebSocket disconnected');
      this.ws = null;
      this.scheduleReconnect();
    };

    socket.onerror = (err) => {
      console.error('WebSocket error:', err);
    };
  }

  disconnect() {
    this.shouldReconnect = false;
    this.clearReconnectTimer();
    if (this.ws) {
      this.ws.onclose = null;
      this.ws.close();
      this.ws = null;
    }
    this.token = null;
    this.pendingFrames = [];
  }

  onMessage(callback: MessageCallback) {
    this.messageCallbacks.push(callback);
    return () => {
      this.messageCallbacks = this.messageCallbacks.filter(cb => cb !== callback);
    };
  }

  onOpen(callback: () => void) {
    this.openCallbacks.push(callback);
    return () => {
      this.openCallbacks = this.openCallbacks.filter(cb => cb !== callback);
    };
  }

  send(data: WebSocketFrame) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data));
      return;
    }

    this.pendingFrames.push(data);
    if (this.pendingFrames.length > 100) {
      this.pendingFrames = this.pendingFrames.slice(-100);
    }
  }

  private scheduleReconnect() {
    if (!this.shouldReconnect || !this.token || this.reconnectTimer) return;

    const delay = Math.min(1000 * 2 ** this.reconnectAttempt, 15000);
    this.reconnectAttempt += 1;
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null;
      if (this.shouldReconnect && this.token) {
        this.connect(this.token);
      }
    }, delay);
  }

  private clearReconnectTimer() {
    if (!this.reconnectTimer) return;
    window.clearTimeout(this.reconnectTimer);
    this.reconnectTimer = null;
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

  queryPresence(userIds: string[]) {
    if (userIds.length === 0) return;
    this.send({
      type: 'presence.query',
      data: {
        userIds
      }
    });
  }

  watchPresence(userIds: string[]) {
    if (userIds.length === 0) return;
    this.send({
      type: 'presence.watch',
      data: {
        userIds
      }
    });
  }

  unwatchPresence(userIds: string[]) {
    if (userIds.length === 0) return;
    this.send({
      type: 'presence.unwatch',
      data: {
        userIds
      }
    });
  }
}

export const wsService = new WebSocketService();
