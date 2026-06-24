import api from './api';

export const authService = {
  login: async (phone: string, _password: string) => {
    try {
      const response = await api.post('/auth/login', {
        phoneNumber: phone,
        password: _password
      });

      if (response.data.status === 'success') {
        return {
          success: true,
          token: response.data.data.accessToken,
          refreshToken: response.data.data.refreshToken,
          user: {
            id: response.data.data.user?.id,
            username: phone,
            displayName: response.data.data.user?.displayName || phone,
            avatarUrl: response.data.data.user?.avatarUrl || `https://ui-avatars.com/api/?name=${phone}&background=0068ff&color=fff`
          }
        };
      }
      throw new Error(response.data.message || 'Đăng nhập thất bại');
    } catch (error: any) {
      if (error.response?.data?.message) {
        throw new Error(error.response.data.message);
      }
      if (error.message) {
        throw error;
      }
      throw new Error('Tài khoản hoặc mật khẩu không chính xác');
    }
  },

  signup: async (phone: string, password: string, displayName: string) => {
    try {
      const response = await api.post('/auth/register', {
        phoneNumber: phone,
        password: password,
        displayName: displayName
      });

      if (response.data.status === 'success') {
        return {
          success: true
        };
      }
      throw new Error(response.data.message || 'Đăng ký thất bại');
    } catch (error: any) {
      if (error.response?.data?.message) {
        throw new Error(error.response.data.message);
      }
      if (error.message) {
        throw error;
      }
      throw new Error('Lỗi mạng hoặc server không phản hồi (Mã lỗi: 999)');
    }
  }
};
