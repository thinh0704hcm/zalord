import api from './api';

export interface InboxItemResponse {
  conversationId: string;
  otherUserId: string | null;
  lastMessagePreview: string | null;
  lastMessageAt: string | null;
  lastSenderId: string | null;
  unreadCount: number;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export const inboxService = {
  list: async (page = 1, size = 50): Promise<PageResponse<InboxItemResponse>> => {
    try {
      const response = await api.get('/inbox', { params: { page, size } });
      return response.data.data;
    } catch (error: any) {
      if (error.response?.data?.message) {
        throw new Error(error.response.data.message);
      }
      throw new Error('Không thể tải danh sách chat');
    }
  }
};
