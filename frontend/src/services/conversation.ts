import api from './api';

export interface ConversationResponse {
  id: string;
  type: 'DIRECT' | 'GROUP';
  memberIds: string[];
  createdAt: string;
}

export const conversationService = {
  createDirect: async (targetUserId: string): Promise<ConversationResponse> => {
    try {
      const response = await api.post('/conversations', {
        type: 'DIRECT',
        memberUserId: targetUserId
      });
      return response.data.data;
    } catch (error: any) {
      if (error.response?.data?.message) {
        throw new Error(error.response.data.message);
      }
      throw new Error('Không thể tạo hoặc lấy cuộc trò chuyện');
    }
  }
};
