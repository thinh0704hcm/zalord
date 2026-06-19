package zalord.group_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zalord.group_service.dto.request.AddMemberRequest;
import zalord.group_service.dto.request.CreateGroupRequest;
import zalord.group_service.dto.request.UpdateGroupRequest;
import zalord.group_service.dto.response.GroupResponse;
import zalord.group_service.dto.response.PageResponse;
import zalord.group_service.model.ApiResponse;
import zalord.group_service.service.IGroupService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups")
@Tag(name = "Groups", description = "Create / manage group chats. Group IDs are reused as conversation IDs in message-service.")
public class GroupController {

    private final IGroupService service;

    public GroupController(IGroupService service) {
        this.service = service;
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create a group",
            description = "Caller becomes OWNER. memberIds = other initial members (caller is auto-added). " +
                    "Publishes group.created which message-service consumes to seed the Conversation.")
    public ResponseEntity<ApiResponse<GroupResponse>> create(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID callerUserId,
            @Valid @RequestBody CreateGroupRequest request) {
        GroupResponse data = service.create(callerUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(HttpStatus.CREATED, "Group created", data, null));
    }

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List my groups (paginated)")
    public ResponseEntity<ApiResponse<PageResponse<GroupResponse>>> listMine(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID callerUserId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<GroupResponse> data = service.listMine(callerUserId, page, size);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK, "OK", data, null));
    }

    @GetMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get group by id (members only)")
    public ResponseEntity<ApiResponse<GroupResponse>> get(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID callerUserId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK, "OK", service.get(callerUserId, id), null));
    }

    @PatchMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update group name / avatar (ADMIN or OWNER only)")
    public ResponseEntity<ApiResponse<GroupResponse>> update(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID callerUserId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateGroupRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK, "Group updated", service.update(callerUserId, id, request), null));
    }

    @PostMapping("/{id}/members")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Add a member to a group (ADMIN or OWNER only)")
    public ResponseEntity<ApiResponse<GroupResponse>> addMember(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID callerUserId,
            @PathVariable UUID id,
            @Valid @RequestBody AddMemberRequest request) {
        GroupResponse data = service.addMember(callerUserId, id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(HttpStatus.CREATED, "Member added", data, null));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Remove a member (ADMIN+ to remove others; any member can remove self)",
            description = "Removing self == leaving the group. OWNER cannot be removed (transfer ownership first).")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID callerUserId,
            @PathVariable UUID id,
            @PathVariable UUID userId) {
        service.removeMember(callerUserId, id, userId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK, "Member removed", null, null));
    }

    @PostMapping("/{id}/leave")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Leave a group (shortcut for DELETE /members/<self>)")
    public ResponseEntity<ApiResponse<Void>> leave(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID callerUserId,
            @PathVariable UUID id) {
        service.leave(callerUserId, id);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK, "Left group", null, null));
    }
}
