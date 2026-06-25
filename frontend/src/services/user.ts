import api from './api';

export interface UserProfile {
  id: string;
  userId: string;
  phoneNumber: string;
  displayName: string;
  avatarUrl: string | null;
  bio: string | null;
  gender?: string | null;
  dateOfBirth?: string | null;
  notificationsEnabled?: boolean;
}

export type UpdateProfilePayload = {
  displayName: string;
  avatarUrl?: string | null;
  gender?: string | null;
  dateOfBirth?: string | null;
  notificationsEnabled?: boolean;
};

type ApiErrorShape = {
  response?: {
    data?: {
      message?: unknown;
      error?: unknown;
    };
  };
};

const getApiErrorMessage = (error: unknown, fallback: string) => {
  if (typeof error === 'object' && error !== null && 'response' in error) {
    const data = (error as ApiErrorShape).response?.data;
    if (typeof data?.message === 'string') return data.message;
    if (typeof data?.error === 'string') return data.error;
  }
  return fallback;
};

export const userService = {
  me: async (): Promise<UserProfile> => {
    try {
      const response = await api.get('/users/me');
      return response.data;
    } catch (error: unknown) {
      throw new Error(getApiErrorMessage(error, 'Không thể tải thông tin tài khoản'), { cause: error });
    }
  },

  updateMe: async (payload: UpdateProfilePayload): Promise<UserProfile> => {
    try {
      const response = await api.put('/users/me', payload);
      return response.data;
    } catch (error: unknown) {
      throw new Error(getApiErrorMessage(error, 'Không thể cập nhật thông tin tài khoản'), { cause: error });
    }
  },

  findByPhone: async (phone: string): Promise<UserProfile> => {
    try {
      const response = await api.get(`/users/by-phone/${phone}`);
      return response.data;
    } catch (error: unknown) {
      throw new Error(getApiErrorMessage(error, 'Không tìm thấy người dùng này'), { cause: error });
    }
  },

  findByUserId: async (userId: string): Promise<UserProfile> => {
    try {
      const response = await api.get(`/users/${userId}`);
      return response.data;
    } catch (error: unknown) {
      throw new Error(getApiErrorMessage(error, 'Không tìm thấy người dùng này'), { cause: error });
    }
  },

  searchByName: async (name: string): Promise<UserProfile[]> => {
    try {
      const response = await api.get('/users/search', { params: { name, limit: 10 } });
      return response.data;
    } catch (error: unknown) {
      throw new Error(getApiErrorMessage(error, 'Không thể tìm người dùng'), { cause: error });
    }
  }
};
