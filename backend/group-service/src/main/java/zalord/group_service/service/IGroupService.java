package zalord.group_service.service;

import zalord.group_service.dto.request.AddMemberRequest;
import zalord.group_service.dto.request.CreateGroupRequest;
import zalord.group_service.dto.request.UpdateGroupRequest;
import zalord.group_service.dto.response.GroupResponse;
import zalord.group_service.dto.response.PageResponse;

import java.util.UUID;

public interface IGroupService {
    GroupResponse create(UUID callerUserId, CreateGroupRequest request);
    GroupResponse get(UUID callerUserId, UUID groupId);
    PageResponse<GroupResponse> listMine(UUID callerUserId, int page, int size);
    GroupResponse update(UUID callerUserId, UUID groupId, UpdateGroupRequest request);
    GroupResponse addMember(UUID callerUserId, UUID groupId, AddMemberRequest request);
    void removeMember(UUID callerUserId, UUID groupId, UUID userId);
    void leave(UUID callerUserId, UUID groupId);
}
