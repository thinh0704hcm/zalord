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
  }
};
