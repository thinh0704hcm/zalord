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

export const inboxService = {
  list: async (page = 1, size = 50): Promise<PageResponse<InboxItemResponse>> => {
    try {
      const response = await api.get('/inbox', { params: { page, size } });
      return response.data.data;
    } catch (error: unknown) {
      throw new Error(getApiErrorMessage(error, 'Không thể tải danh sách chat'), { cause: error });
    }
  },

  markRead: async (conversationId: string, messageId?: string): Promise<void> => {
    try {
      await api.post(`/inbox/${conversationId}/read`, messageId ? { messageId } : {});
    } catch (error: unknown) {
      throw new Error(getApiErrorMessage(error, 'Không thể cập nhật trạng thái đã xem'), { cause: error });
    }
  }
};
