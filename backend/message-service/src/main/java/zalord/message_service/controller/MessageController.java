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
import zalord.message_service.dto.response.MessageResponse;
import zalord.message_service.dto.response.PageResponse;
import zalord.message_service.model.ApiResponse;
import zalord.message_service.service.IMessageService;

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

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get a conversation's message history (paginated, newest first)",
            description = "Caller must be a member of the conversation.")
    public ResponseEntity<ApiResponse<PageResponse<MessageResponse>>> history(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID callerUserId,
            @RequestParam UUID conversationId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        PageResponse<MessageResponse> data = service.history(callerUserId, conversationId, page, size);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK, "OK", data, null));
    }
}
