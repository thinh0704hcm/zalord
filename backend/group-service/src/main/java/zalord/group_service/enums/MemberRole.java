package zalord.group_service.enums;

public enum MemberRole {
    OWNER,   // creator; can transfer ownership, delete group
    ADMIN,   // can add/remove members, rename, change avatars
    MEMBER   // can send messages, leave on their own
}
