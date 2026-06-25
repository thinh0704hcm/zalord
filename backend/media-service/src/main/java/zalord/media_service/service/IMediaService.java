package zalord.media_service.service;

import zalord.media_service.dto.request.UploadUrlRequest;
import zalord.media_service.dto.response.DownloadUrlResponse;
import zalord.media_service.dto.response.MediaResponse;
import zalord.media_service.dto.response.UploadUrlResponse;

import java.util.UUID;

public interface IMediaService {
    UploadUrlResponse requestUploadUrl(UUID callerUserId, UploadUrlRequest request);
    MediaResponse finalize(UUID callerUserId, UUID mediaId);
    MediaResponse get(UUID callerUserId, UUID mediaId);
    DownloadUrlResponse downloadUrl(UUID callerUserId, UUID mediaId);
    void delete(UUID callerUserId, UUID mediaId);
    byte[] downloadAvatar(UUID callerUserId, UUID mediaId);
}
