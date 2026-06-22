package zalord.media_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zalord.media_service.dto.request.UploadUrlRequest;
import zalord.media_service.dto.response.DownloadUrlResponse;
import zalord.media_service.dto.response.MediaResponse;
import zalord.media_service.dto.response.UploadUrlResponse;
import zalord.media_service.model.ApiResponse;
import zalord.media_service.service.IMediaService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
@Tag(name = "Media", description = "Avatar + chat attachment uploads via S3-style presigned URLs (backed by MinIO)")
public class MediaController {

    private final IMediaService service;

    public MediaController(IMediaService service) {
        this.service = service;
    }

    @PostMapping("/upload-url")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Request a presigned PUT URL to upload media",
            description = "Two-step flow: 1) call this → get {mediaId, uploadUrl}. 2) PUT the file bytes to uploadUrl directly (browser → MinIO). 3) POST /api/v1/media/{id}/finalize to confirm. URL expires in 15 minutes. " +
                    "For ATTACHMENT, caller must be a member of the target conversation (checked via Redis cache populated by message-service).")
    public ResponseEntity<ApiResponse<UploadUrlResponse>> upload(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID callerUserId,
            @Valid @RequestBody UploadUrlRequest request) {
        UploadUrlResponse data = service.requestUploadUrl(callerUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(HttpStatus.CREATED, "Upload URL issued", data, null));
    }

    @PostMapping("/{id}/finalize")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Finalize an upload",
            description = "Confirms the bytes are in MinIO. Service does a headObject to verify + read content-type and size. Idempotent.")
    public ResponseEntity<ApiResponse<MediaResponse>> finalize(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID callerUserId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK, "Finalized", service.finalize(callerUserId, id), null));
    }

    @GetMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get media metadata (no URL)")
    public ResponseEntity<ApiResponse<MediaResponse>> get(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID callerUserId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK, "OK", service.get(callerUserId, id), null));
    }

    @GetMapping("/{id}/url")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get a presigned GET URL to download the media",
            description = "Authz: AVATAR open to any authenticated user; ATTACHMENT requires caller to be a member of the conversation. URL expires in 15 minutes.")
    public ResponseEntity<ApiResponse<DownloadUrlResponse>> downloadUrl(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID callerUserId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK, "OK", service.downloadUrl(callerUserId, id), null));
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Soft-delete media (owner only)")
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID callerUserId,
            @PathVariable UUID id) {
        service.delete(callerUserId, id);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK, "Deleted", null, null));
    }
}
