// src/services/auth.ts
// Mock API service for authentication. 
// Can be easily swapped with real fetch/axios calls later.

export const authService = {
  login: async (username: string, _password: string) => {
    // Simulate network delay
    await new Promise((resolve) => setTimeout(resolve, 800));

    if (username === 'mock' && _password === 'mock123') {
      return {
        success: true,
        token: 'mock-jwt-token-xyz',
        user: {
          id: '1',
          username: 'mock',
          displayName: 'Mock User',
          avatarUrl: 'https://ui-avatars.com/api/?name=Mock+User&background=0068ff&color=fff'
        }
      };
    }
    
    throw new Error('Tài khoản hoặc mật khẩu không chính xác');
  },

  signup: async (username: string, password: string, displayName: string) => {
    await new Promise((resolve) => setTimeout(resolve, 800));

    // For mocking purposes, only allow registering 'mock'
    if (username === 'mock') {
      return {
        success: true,
        token: 'mock-jwt-token-xyz',
        user: {
          id: '1',
          username: 'mock',
          displayName: displayName,
          avatarUrl: `https://ui-avatars.com/api/?name=${encodeURIComponent(displayName)}&background=0068ff&color=fff`
        }
      };
    }

    throw new Error('Tên đăng nhập đã tồn tại');
  }
};
