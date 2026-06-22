import api from './api';

export interface UserProfile {
  id: string;
  phoneNumber: string;
  displayName: string;
  avatarUrl: string | null;
  bio: string | null;
}

export const userService = {
  findByPhone: async (phone: string): Promise<UserProfile> => {
    try {
      const response = await api.get(`/users/by-phone/${phone}`);
      // The backend returns the profile object directly
      return response.data;
    } catch (error: any) {
      if (error.response?.data?.message) {
        throw new Error(error.response.data.message);
      }
      throw new Error('Không tìm thấy người dùng này');
    }
  }
};
