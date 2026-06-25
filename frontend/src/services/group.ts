import api from './api';

export interface GroupMemberResponse {
  userId: string;
  role: 'OWNER' | 'ADMIN' | 'MEMBER';
  joinedAt: string;
}

export interface GroupResponse {
  id: string;
  name: string;
  avatarUrl: string | null;
  createdBy: string;
  createdAt: string;
  members: GroupMemberResponse[];
}

export interface CreateGroupRequest {
  name: string;
  avatarUrl?: string | null;
  memberIds: string[];
}

export const groupService = {
  create: async (request: CreateGroupRequest): Promise<GroupResponse> => {
    try {
      const response = await api.post('/groups', request);
      return response.data.data;
    } catch (error: any) {
      if (error.response?.data?.message) {
        throw new Error(error.response.data.message);
      }
      throw new Error('Không thể tạo nhóm');
    }
  },

  get: async (groupId: string): Promise<GroupResponse> => {
    try {
      const response = await api.get(`/groups/${groupId}`);
      return response.data.data;
    } catch (error: any) {
      if (error.response?.data?.message) {
        throw new Error(error.response.data.message);
      }
      throw new Error('Không thể tải thông tin nhóm');
    }
  },

  addMember: async (groupId: string, userId: string): Promise<GroupResponse> => {
    try {
      const response = await api.post(`/groups/${groupId}/members`, { userId, role: 'MEMBER' });
      return response.data.data;
    } catch (error: any) {
      if (error.response?.data?.message) {
        throw new Error(error.response.data.message);
      }
      throw new Error('Không thể thêm thành viên');
    }
  },

  removeMember: async (groupId: string, userId: string): Promise<void> => {
    try {
      await api.delete(`/groups/${groupId}/members/${userId}`);
    } catch (error: any) {
      if (error.response?.data?.message) {
        throw new Error(error.response.data.message);
      }
      throw new Error('Không thể xóa thành viên');
    }
  }
};
