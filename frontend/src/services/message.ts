import api from './api';

export interface SendMessageRequest {
  conversationId: string;
  content?: string;
  attachmentIds?: string[];
}

export const messageService = {
  send: async (request: SendMessageRequest) => {
    try {
      const response = await api.post('/messages', request);
      return response.data.data;
    } catch (error: any) {
      if (error.response?.data?.message) {
        throw new Error(error.response.data.message);
      }
      throw new Error('Không thể gửi tin nhắn');
    }
  },
  history: async (conversationId: string, page = 1, size = 50) => {
    try {
      const response = await api.get('/messages', {
        params: { conversationId, page, size }
      });
      return response.data.data; // This is a PageResponse containing content
    } catch (error: any) {
      console.error('Failed to load message history:', error);
      return { content: [], totalPages: 0 };
    }
  }
};
