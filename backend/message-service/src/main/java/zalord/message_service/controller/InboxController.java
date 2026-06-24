package zalord.message_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zalord.message_service.dto.request.MarkReadRequest;
import zalord.message_service.dto.response.InboxItemResponse;
import zalord.message_service.dto.response.PageResponse;
import zalord.message_service.model.ApiResponse;
import zalord.message_service.service.IInboxService;

import java.util.UUID;

/**
 * CQRS query side of message-service. Reads the conversation_views projection
 * built by InboxProjector. Frontend uses this for the inbox/conversation list
 * screen — ONE query, no JOINs, no cross-service lookups.
 */
@RestController
@RequestMapping("/api/v1/inbox")
@Tag(name = "Inbox", description = "CQRS read model: denormalized conversation list (last message + unread count)")
public class InboxController {

    private final IInboxService service;

    public InboxController(IInboxService service) {
        this.service = service;
    }

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get my inbox (conversation list)",
            description = "Returns conversations sorted by last_message_at DESC, with cached preview and unread count. Eventual consistency: ~milliseconds after POST /messages.")
    public ResponseEntity<ApiResponse<PageResponse<InboxItemResponse>>> list(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID callerUserId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<InboxItemResponse> data = service.listInbox(callerUserId, page, size);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK, "OK", data, null));
    }

    @PostMapping("/{conversationId}/read")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Mark a conversation as read",
            description = "Resets unread_count to 0 for the caller's view of this conversation and publishes a message.read event so other members' UIs can update their \"Seen\" markers. Optional body { messageId } pins which message was last read; if omitted, the latest message in the conversation is used. No-op if no view exists yet.")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID callerUserId,
            @PathVariable UUID conversationId,
            @RequestBody(required = false) MarkReadRequest body) {
        UUID messageId = body == null ? null : body.messageId();
        service.markRead(callerUserId, conversationId, messageId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK, "Marked read", null, null));
    }
}
