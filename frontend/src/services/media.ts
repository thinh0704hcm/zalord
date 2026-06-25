import api from './api';
import axios from 'axios';

export interface UploadUrlRequest {
  kind: 'AVATAR' | 'ATTACHMENT';
  conversationId?: string;
  mimeType?: string;
}

export interface UploadUrlResponse {
  mediaId: string;
  uploadUrl: string;
}

export interface MediaResponse {
  id: string;
  kind: string;
  conversationId?: string;
  url?: string;
  mimeType?: string;
  sizeBytes?: number;
}

export const mediaService = {
  requestUploadUrl: async (request: UploadUrlRequest): Promise<UploadUrlResponse> => {
    const response = await api.post('/media/upload-url', request);
    return response.data.data;
  },
  
  uploadFileToMinio: async (uploadUrl: string, file: File): Promise<void> => {
    await axios.put(uploadUrl, file, {
      headers: {
        'Content-Type': file.type || 'application/octet-stream',
      },
    });
  },
  
  finalizeUpload: async (mediaId: string): Promise<MediaResponse> => {
    const response = await api.post(`/media/${mediaId}/finalize`);
    return response.data.data;
  },

  uploadAttachment: async (conversationId: string, file: File): Promise<MediaResponse> => {
    const { mediaId, uploadUrl } = await mediaService.requestUploadUrl({
      kind: 'ATTACHMENT',
      conversationId,
      mimeType: file.type || 'application/octet-stream',
    });
    
    await mediaService.uploadFileToMinio(uploadUrl, file);
    return await mediaService.finalizeUpload(mediaId);
  }
};
