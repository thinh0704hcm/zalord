import api from './api';

export interface SendMessageRequest {
  conversationId: string;
  content?: string;
  attachmentIds?: string[];
}

export interface MessageReaderResponse {
  userId: string;
  readAt: string;
}

type ApiErrorShape = {
  response?: {
    data?: {
      message?: unknown;
    };
  };
};

const getApiErrorMessage = (error: unknown, fallback: string) => {
  if (typeof error === 'object' && error !== null && 'response' in error) {
    const data = (error as ApiErrorShape).response?.data;
    if (typeof data?.message === 'string') return data.message;
  }
  return fallback;
};

export const messageService = {
  send: async (request: SendMessageRequest) => {
    try {
      const response = await api.post('/messages', request);
      return response.data.data;
    } catch (error: unknown) {
      throw new Error(getApiErrorMessage(error, 'Không thể gửi tin nhắn'), { cause: error });
    }
  },
  history: async (conversationId: string, page = 1, size = 50) => {
    try {
      const response = await api.get('/messages', {
        params: { conversationId, page, size }
      });
      return response.data.data; // This is a PageResponse containing content
    } catch (error: unknown) {
      console.error('Failed to load message history:', error);
      return { content: [], totalPages: 0 };
    }
  },
  lastReaders: async (conversationId: string): Promise<MessageReaderResponse[]> => {
    try {
      const response = await api.get(`/messages/conversations/${conversationId}/last-readers`);
      return response.data.data ?? [];
    } catch (error: unknown) {
      console.error('Failed to load latest message readers:', error);
      return [];
    }
  }
};
