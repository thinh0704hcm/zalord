package zalord.message_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zalord.message_service.dto.request.SendMessageRequest;
import zalord.message_service.dto.response.MessageReaderResponse;
import zalord.message_service.dto.response.MessageResponse;
import zalord.message_service.dto.response.PageResponse;
import zalord.message_service.model.ApiResponse;
import zalord.message_service.service.IMessageService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/messages")
@Tag(name = "Messages", description = "Send and read messages within a conversation")
public class MessageController {

    private final IMessageService service;

    public MessageController(IMessageService service) {
        this.service = service;
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Send a message in a conversation",
            description = "Persists the message and queues a message.created event for downstream consumers (chat-service WebSocket fan-out, etc).")
    public ResponseEntity<ApiResponse<MessageResponse>> send(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID callerUserId,
            @Valid @RequestBody SendMessageRequest request) {
        MessageResponse data = service.send(callerUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(HttpStatus.CREATED, "Message sent", data, null));
    }

    @GetMapping("/conversations/{conversationId}/last-readers")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get readers of the latest message",
            description = "Returns conversation members, excluding the caller, whose read cursor is on the latest message in the conversation.")
    public ResponseEntity<ApiResponse<List<MessageReaderResponse>>> lastMessageReaders(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID callerUserId,
            @PathVariable UUID conversationId) {
        List<MessageReaderResponse> data = service.lastMessageReaders(callerUserId, conversationId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK, "OK", data, null));
    }

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get a conversation's message history (paginated, newest first)",
            description = "Caller must be a member of the conversation.")
    public ResponseEntity<ApiResponse<PageResponse<MessageResponse>>> history(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID callerUserId,
            @RequestParam UUID conversationId,
            @RequestParam(required = false) java.time.Instant cursor,
            @RequestParam(defaultValue = "50") int size) {
        PageResponse<MessageResponse> data = service.history(callerUserId, conversationId, cursor, size);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK, "OK", data, null));
    }

    @DeleteMapping("/{messageId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Recall a message (\"thu hồi cho tất cả\")",
            description = "Marks the message as retracted for everyone in the conversation. Only the sender can recall their own message. The row stays in the DB so pagination + reply snapshots don't break; clients render \"Tin nhắn đã được thu hồi\" in place of the body. Publishes message.recalled — live clients update instantly via WebSocket.")
    public ResponseEntity<ApiResponse<Void>> recall(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID callerUserId,
            @PathVariable UUID messageId) {
        service.recall(callerUserId, messageId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK, "Recalled", null, null));
    }
}
