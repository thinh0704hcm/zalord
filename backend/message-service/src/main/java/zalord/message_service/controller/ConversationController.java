package zalord.message_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zalord.message_service.dto.request.CreateConversationRequest;
import zalord.message_service.dto.response.ConversationResponse;
import zalord.message_service.dto.response.PageResponse;
import zalord.message_service.model.ApiResponse;
import zalord.message_service.service.IConversationService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations")
@Tag(name = "Conversations", description = "Create / list / fetch chat conversations")
public class ConversationController {

    private final IConversationService service;

    public ConversationController(IConversationService service) {
        this.service = service;
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create or fetch a DIRECT conversation",
            description = "POST is idempotent for DIRECT type: if you and the target user already have a conversation, the existing one is returned (no duplicate). The caller is identified by Kong's injected X-User-Id header — do not pass it from the client.")
    public ResponseEntity<ApiResponse<ConversationResponse>> create(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID callerUserId,
            @Valid @RequestBody CreateConversationRequest request) {
        ConversationResponse data = service.create(callerUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(HttpStatus.CREATED, "Conversation ready", data, null));
    }

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List my conversations (paginated)",
            description = "Returns conversations the caller is a member of, most recently joined first.")
    public ResponseEntity<ApiResponse<PageResponse<ConversationResponse>>> listMine(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID callerUserId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<ConversationResponse> data = service.listMine(callerUserId, page, size);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK, "OK", data, null));
    }

    @GetMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get a conversation by id (members only)")
    public ResponseEntity<ApiResponse<ConversationResponse>> get(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID callerUserId,
            @PathVariable UUID id) {
        ConversationResponse data = service.get(callerUserId, id);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK, "OK", data, null));
    }
}
