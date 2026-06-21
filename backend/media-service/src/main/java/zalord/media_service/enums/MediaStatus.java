package zalord.media_service.enums;

public enum MediaStatus {
    PENDING,    // upload URL issued, byte upload not confirmed yet
    READY,      // finalized — bytes verified present in MinIO
    DELETED     // soft-deleted
}
