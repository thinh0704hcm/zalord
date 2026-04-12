package io.zalord.chat.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.zalord.chat.application.ChatService;
import io.zalord.chat.application.commands.CreateChatCommand;
import io.zalord.chat.application.commands.DeleteChatCommand;
import io.zalord.chat.application.commands.LeaveChatCommand;
import io.zalord.chat.application.commands.RemoveFromChatCommand;
import io.zalord.chat.application.commands.TransferChatOwnershipCommand;
import io.zalord.chat.application.commands.UpdateChatCommand;
import io.zalord.chat.application.commands.UpdateMemberRoleCommand;
import io.zalord.chat.dto.request.CreateChatRequest;
import io.zalord.chat.dto.request.TransferChatOwnershipRequest;
import io.zalord.chat.dto.request.UpdateChatRequest;
import io.zalord.chat.dto.request.UpdateMemberRoleRequest;
import io.zalord.chat.dto.response.ChatResponse;
import io.zalord.common.security.AuthenticatedUser;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/create")
    public ResponseEntity<ChatResponse> createChat(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CreateChatRequest request) {
        CreateChatCommand cmd = new CreateChatCommand(
                actor.userId(),
                request.chatName(),
                request.chatType(),
                request.memberIds());

        ChatResponse response = chatService.createChat(cmd);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{chatId}")
    public ResponseEntity<ChatResponse> updateChat(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID chatId,
            @Valid @RequestBody UpdateChatRequest request) {
        ChatResponse response = chatService
                .updateChat(new UpdateChatCommand(actor.userId(), chatId, request.chatName()));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @DeleteMapping("/{chatId}")
    public ResponseEntity<Void> deleteChat(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID chatId) {
        chatService.deleteChat(new DeleteChatCommand(actor.userId(), chatId));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{chatId}/members/{memberId}/role")
    public ResponseEntity<Void> updateMemberRole(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID chatId,
            @PathVariable UUID memberId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        chatService.updateMemberRole(new UpdateMemberRoleCommand(actor.userId(), chatId, memberId, request.role()));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{chatId}/members/{memberId}")
    public ResponseEntity<Void> removeMemberFromChat(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID chatId,
            @PathVariable UUID memberId) {
        chatService.removeFromChat(new RemoveFromChatCommand(actor.userId(), chatId, memberId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{chatId}/transfer")
    public ResponseEntity<Void> transferOwnership(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID chatId,
            @Valid @RequestBody TransferChatOwnershipRequest request) {
        chatService
                .transferChatOwnership(new TransferChatOwnershipCommand(actor.userId(), chatId, request.recipientId()));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{chatId}/members/me")
    public ResponseEntity<Void> leaveChat(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID chatId) {
        chatService.leaveChat(new LeaveChatCommand(actor.userId(), chatId));
        return ResponseEntity.noContent().build();
    }
}
