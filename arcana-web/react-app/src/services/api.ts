import axios, { AxiosInstance, AxiosRequestConfig } from 'axios';

/**
 * API Client for Arcana Cloud backend.
 */
class ApiClient {
  private client: AxiosInstance;

  constructor() {
    this.client = axios.create({
      baseURL: process.env.API_BASE_URL || '/api',
      timeout: 30000,
      headers: {
        'Content-Type': 'application/json',
      },
    });

    // Request interceptor
    this.client.interceptors.request.use(
      (config) => {
        // Add auth token if available
        const token =
          typeof window !== 'undefined'
            ? localStorage.getItem('arcana_token')
            : null;
        if (token) {
          config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
      },
      (error) => Promise.reject(error)
    );

    // Response interceptor
    this.client.interceptors.response.use(
      (response) => response,
      (error) => {
        if (error.response?.status === 401) {
          // Handle unauthorized
          if (typeof window !== 'undefined') {
            localStorage.removeItem('arcana_token');
            window.location.href = '/login';
          }
        }
        return Promise.reject(error);
      }
    );
  }

  async get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    const response = await this.client.get<T>(url, config);
    return response.data;
  }

  async post<T>(
    url: string,
    data?: unknown,
    config?: AxiosRequestConfig
  ): Promise<T> {
    const response = await this.client.post<T>(url, data, config);
    return response.data;
  }

  async put<T>(
    url: string,
    data?: unknown,
    config?: AxiosRequestConfig
  ): Promise<T> {
    const response = await this.client.put<T>(url, data, config);
    return response.data;
  }

  async delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    const response = await this.client.delete<T>(url, config);
    return response.data;
  }

  async patch<T>(
    url: string,
    data?: unknown,
    config?: AxiosRequestConfig
  ): Promise<T> {
    const response = await this.client.patch<T>(url, data, config);
    return response.data;
  }
}

export const api = new ApiClient();

// Auth API
export const authApi = {
  login: (email: string, password: string) =>
    api.post<{ token: string; user: User }>('/v1/auth/login', {
      email,
      password,
    }),

  register: (data: RegisterData) =>
    api.post<{ token: string; user: User }>('/v1/auth/register', data),

  logout: () => api.post('/v1/auth/logout'),

  me: () => api.get<User>('/v1/auth/me'),
};

// Plugin API
export const pluginApi = {
  list: () => api.get<PluginInfo[]>('/v1/plugins'),

  get: (key: string) => api.get<PluginInfo>(`/v1/plugins/${key}`),

  enable: (key: string) => api.post(`/v1/plugins/${key}/enable`),

  disable: (key: string) => api.post(`/v1/plugins/${key}/disable`),

  install: (file: File) => {
    const formData = new FormData();
    formData.append('plugin', file);
    return api.post<PluginInfo>('/v1/plugins/install', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },

  uninstall: (key: string) => api.delete(`/v1/plugins/${key}`),

  getConfig: (key: string) => api.get<Record<string, unknown>>(`/v1/plugins/${key}/config`),

  updateConfig: (key: string, config: Record<string, unknown>) =>
    api.put(`/v1/plugins/${key}/config`, config),
};

// Audit API
export const auditApi = {
  list: (params?: AuditQueryParams) =>
    api.get<PaginatedResponse<AuditEntry>>('/v1/plugins/audit/entries', {
      params,
    }),

  get: (id: number) => api.get<AuditEntry>(`/v1/plugins/audit/entries/${id}`),

  search: (query: string, params?: AuditQueryParams) =>
    api.get<PaginatedResponse<AuditEntry>>('/v1/plugins/audit/entries/search', {
      params: { q: query, ...params },
    }),
};

// Types
export interface User {
  id: number;
  email: string;
  username: string;
  firstName?: string;
  lastName?: string;
  roles: string[];
}

export interface RegisterData {
  email: string;
  username: string;
  password: string;
  firstName?: string;
  lastName?: string;
}

export interface PluginInfo {
  key: string;
  name: string;
  version: string;
  description?: string;
  vendor?: string;
  enabled: boolean;
  state: 'INSTALLED' | 'RESOLVED' | 'STARTING' | 'ACTIVE' | 'STOPPING';
  views: string[];
  extensions: string[];
}

export interface AuditEntry {
  id: number;
  userId: number;
  username: string;
  action: string;
  entityType: string;
  entityId: string;
  details: Record<string, unknown>;
  ipAddress: string;
  userAgent: string;
  timestamp: string;
}

export interface AuditQueryParams {
  userId?: number;
  action?: string;
  entityType?: string;
  startDate?: string;
  endDate?: string;
  page?: number;
  size?: number;
}

export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}
