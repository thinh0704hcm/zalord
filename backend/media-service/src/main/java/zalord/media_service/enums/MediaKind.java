package zalord.media_service.enums;

public enum MediaKind {
    AVATAR,       // user profile picture; public-read (no membership check)
    ATTACHMENT    // file in a chat message; private, gated by conversation membership
}
