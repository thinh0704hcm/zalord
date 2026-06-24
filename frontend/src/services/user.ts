import api from './api';

export interface UserProfile {
  id: string;
  userId: string;
  phoneNumber: string;
  displayName: string;
  avatarUrl: string | null;
  bio: string | null;
}

export const userService = {
  me: async (): Promise<UserProfile> => {
    try {
      const response = await api.get('/users/me');
      return response.data;
    } catch (error: any) {
      if (error.response?.data?.message) {
        throw new Error(error.response.data.message);
      }
      throw new Error('Không thể tải thông tin tài khoản');
    }
  },

  findByPhone: async (phone: string): Promise<UserProfile> => {
    try {
      const response = await api.get(`/users/by-phone/${phone}`);
      return response.data;
    } catch (error: any) {
      if (error.response?.data?.message) {
        throw new Error(error.response.data.message);
      }
      throw new Error('Không tìm thấy người dùng này');
    }
  },

  findByUserId: async (userId: string): Promise<UserProfile> => {
    try {
      const response = await api.get(`/users/${userId}`);
      return response.data;
    } catch (error: any) {
      if (error.response?.data?.message) {
        throw new Error(error.response.data.message);
      }
      throw new Error('Không tìm thấy người dùng này');
    }
  },

  searchByName: async (name: string): Promise<UserProfile[]> => {
    try {
      const response = await api.get('/users/search', { params: { name, limit: 10 } });
      return response.data;
    } catch (error: any) {
      if (error.response?.data?.message) {
        throw new Error(error.response.data.message);
      }
      throw new Error('Không thể tìm người dùng');
    }
  }
};
