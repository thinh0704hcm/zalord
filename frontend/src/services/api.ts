import axios from 'axios';
import type { AxiosError, InternalAxiosRequestConfig } from 'axios';

interface RetriableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

const baseURL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1';

// Create axios instance
const api = axios.create({
  baseURL,
  timeout: 10000,
});

const refreshClient = axios.create({
  baseURL,
  timeout: 10000,
});

const clearAuth = () => {
  localStorage.removeItem('token');
  localStorage.removeItem('refreshToken');
  localStorage.removeItem('user');
  window.dispatchEvent(new Event('auth-expired'));
};

// Request interceptor to attach JWT token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor to refresh expired access tokens once, then retry.
api.interceptors.response.use(
  (response) => {
    return response;
  },
  async (error: AxiosError) => {
    const originalRequest = error.config as RetriableRequestConfig | undefined;

    if (error.response?.status !== 401 || !originalRequest || originalRequest._retry) {
      return Promise.reject(error);
    }

    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) {
      clearAuth();
      return Promise.reject(error);
    }

    originalRequest._retry = true;

    try {
      const response = await refreshClient.post('/auth/refresh', { refreshToken });
      const accessToken = response.data?.data?.accessToken;

      if (!accessToken) {
        clearAuth();
        return Promise.reject(error);
      }

      localStorage.setItem('token', accessToken);
      originalRequest.headers.Authorization = `Bearer ${accessToken}`;
      return api(originalRequest);
    } catch (refreshError) {
      clearAuth();
      return Promise.reject(refreshError);
    }
  }
);

export default api;
