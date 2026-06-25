package zalord.media_service.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import zalord.media_service.config.MinioProperties;
import zalord.media_service.dto.request.UploadUrlRequest;
import zalord.media_service.dto.response.DownloadUrlResponse;
import zalord.media_service.dto.response.MediaResponse;
import zalord.media_service.dto.response.UploadUrlResponse;
import zalord.media_service.enums.MediaKind;
import zalord.media_service.enums.MediaStatus;
import zalord.media_service.exception.ForbiddenException;
import zalord.media_service.exception.InvalidRequestException;
import zalord.media_service.exception.MediaNotFoundException;
import zalord.media_service.model.Media;
import zalord.media_service.repository.MediaRepository;
import zalord.media_service.service.IMediaService;
import zalord.media_service.service.MediaCache;
import zalord.media_service.service.MembershipCache;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class MediaServiceImpl implements IMediaService {

    private static final Duration UPLOAD_URL_TTL   = Duration.ofMinutes(15);
    private static final Duration DOWNLOAD_URL_TTL = Duration.ofMinutes(15);

    private final MediaRepository repo;
    private final MinioProperties props;
    private final S3Client s3;
    private final S3Presigner presigner;
    private final MembershipCache membership;
    private final MediaCache mediaCache;

    public MediaServiceImpl(MediaRepository repo,
                            MinioProperties props,
                            S3Client s3,
                            S3Presigner presigner,
                            MembershipCache membership,
                            MediaCache mediaCache) {
        this.repo = repo;
        this.props = props;
        this.s3 = s3;
        this.presigner = presigner;
        this.membership = membership;
        this.mediaCache = mediaCache;
    }

    @Override
    @Transactional
    public UploadUrlResponse requestUploadUrl(UUID caller, UploadUrlRequest req) {
        if (req.kind() == MediaKind.ATTACHMENT && req.conversationId() == null) {
            throw new InvalidRequestException("conversationId is required for ATTACHMENT");
        }
        // ATTACHMENT upload: also require caller is member of the target conv.
        // (Without this, anyone could upload bytes into a conv they don't own.
        // The download is already gated, but storage abuse would still be possible.)
        if (req.kind() == MediaKind.ATTACHMENT
                && !membership.isMember(req.conversationId(), caller)) {
            throw new ForbiddenException("You are not a member of the target conversation");
        }

        UUID mediaId = UUID.randomUUID();
        String bucket = bucketFor(req.kind());
        String key    = storageKey(req.kind(), caller, req.conversationId(), mediaId);

        // Native INSERT (see repo comment) — bypasses JPA detached-entity issue
        // that fires when @GeneratedValue UUID sees a pre-set id.
        repo.insertNew(
                mediaId,
                caller,
                req.kind().name(),
                req.kind() == MediaKind.ATTACHMENT ? req.conversationId() : null,
                key,
                req.mimeType()
        );

        String url = presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(UPLOAD_URL_TTL)
                        .putObjectRequest(p -> {
                            p.bucket(bucket).key(key);
                            if (req.mimeType() != null) p.contentType(req.mimeType());
                        })
                        .build()
        ).url().toString();

        log.info("Upload URL issued media={} bucket={} owner={}", mediaId, bucket, caller);
        return UploadUrlResponse.builder()
                .mediaId(mediaId)
                .uploadUrl(url)
                .httpMethod("PUT")
                .expiresAt(Instant.now().plus(UPLOAD_URL_TTL))
                .build();
    }

    @Override
    @Transactional
    public MediaResponse finalize(UUID caller, UUID mediaId) {
        Media m = loadOwned(caller, mediaId);
        if (m.getStatus() == MediaStatus.READY) {
            return toResponse(m);
        }
        try {
            HeadObjectResponse head = s3.headObject(b -> b
                    .bucket(bucketFor(m.getKind()))
                    .key(m.getStorageKey()));
            m.setSizeBytes(head.contentLength());
            if (head.contentType() != null) m.setMimeType(head.contentType());
            m.setStatus(MediaStatus.READY);
            m.setFinalizedAt(Instant.now());
            // Warm the cache with the post-finalize snapshot — next validate or
            // download URL request avoids the DB round trip.
            mediaCache.put(MediaCache.Snapshot.of(m));
            log.info("Finalized media={} size={} mime={}", mediaId, head.contentLength(), head.contentType());
            return toResponse(m);
        } catch (NoSuchKeyException ex) {
            throw new InvalidRequestException("No uploaded object found; did the PUT succeed?");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public MediaResponse get(UUID caller, UUID mediaId) {
        Media m = loadActive(mediaId);
        authorizeRead(caller, m);
        return toResponse(m);
    }

    @Override
    @Transactional(readOnly = true)
    public DownloadUrlResponse downloadUrl(UUID caller, UUID mediaId) {
        Media m = loadActive(mediaId);
        if (m.getStatus() != MediaStatus.READY) {
            throw new InvalidRequestException("Media is not finalized yet");
        }
        authorizeRead(caller, m);

        String url = presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(DOWNLOAD_URL_TTL)
                        .getObjectRequest(g -> g
                                .bucket(bucketFor(m.getKind()))
                                .key(m.getStorageKey()))
                        .build()
        ).url().toString();

        return DownloadUrlResponse.builder()
                .downloadUrl(url)
                .expiresAt(Instant.now().plus(DOWNLOAD_URL_TTL))
                .build();
    }

    @Override
    @Transactional
    public void delete(UUID caller, UUID mediaId) {
        Media m = loadActive(mediaId);
        if (!m.getOwnerId().equals(caller)) {
            throw new ForbiddenException("Only the owner can delete this media");
        }
        m.setStatus(MediaStatus.DELETED);
        // Evict cache so in-flight validate calls don't pass on a stale READY
        // snapshot. Subsequent reads see DELETED via Postgres (and re-cache it).
        mediaCache.evict(mediaId);
        // Soft delete only — leaves bytes in MinIO. A separate cron could
        // sweep DELETED rows >N days old and call s3.deleteObject(). Out of scope.
        log.info("Soft-deleted media={}", mediaId);
    }

    // ── authz / helpers ──────────────────────────────────────────────────────

    private void authorizeRead(UUID caller, Media m) {
        if (m.getKind() == MediaKind.AVATAR) {
            // Avatar is public-ish: any authenticated user can fetch.
            return;
        }
        // ATTACHMENT: owner OR conversation member.
        if (m.getOwnerId().equals(caller)) return;
        if (m.getConversationId() != null && membership.isMember(m.getConversationId(), caller)) return;
        throw new ForbiddenException("Not a member of the attachment's conversation");
    }

    private Media loadActive(UUID id) {
        Media m = repo.findById(id).orElseThrow(() -> new MediaNotFoundException("Media not found: " + id));
        if (m.getStatus() == MediaStatus.DELETED) {
            throw new MediaNotFoundException("Media has been deleted: " + id);
        }
        return m;
    }

    private Media loadOwned(UUID caller, UUID id) {
        Media m = loadActive(id);
        if (!m.getOwnerId().equals(caller)) {
            throw new ForbiddenException("Not the owner of this media");
        }
        return m;
    }

    private String bucketFor(MediaKind kind) {
        return kind == MediaKind.AVATAR ? props.getBucket().getAvatars() : props.getBucket().getAttachments();
    }

    private String storageKey(MediaKind kind, UUID owner, UUID conversationId, UUID mediaId) {
        return switch (kind) {
            case AVATAR     -> "users/" + owner + "/" + mediaId;
            case ATTACHMENT -> "conversations/" + conversationId + "/" + mediaId;
        };
    }

    private MediaResponse toResponse(Media m) {
        return MediaResponse.builder()
                .id(m.getId())
                .ownerId(m.getOwnerId())
                .kind(m.getKind())
                .conversationId(m.getConversationId())
                .mimeType(m.getMimeType())
                .sizeBytes(m.getSizeBytes())
                .status(m.getStatus())
                .createdAt(m.getCreatedAt())
                .finalizedAt(m.getFinalizedAt())
                .build();
    }
    @Override
    public byte[] downloadAvatar(UUID callerUserId, UUID mediaId) {
        Media m = loadActive(mediaId);
        if (m.getKind() != MediaKind.AVATAR) {
            throw new InvalidRequestException("Media is not an avatar");
        }

        try {
            return s3.getObject(GetObjectRequest.builder()
                            .bucket(bucketFor(m.getKind()))
                            .key(storageKey(m.getKind(), m.getOwnerId(), m.getConversationId(), m.getId()))
                            .build())
                    .readAllBytes();
        } catch (Exception e) {
            log.error("Failed to download avatar {}", mediaId, e);
            throw new RuntimeException("Failed to download avatar", e);
        }
    }
}
