package io.zalord.presence.api;

import io.zalord.presence.application.PresenceService;

import java.util.Set;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/chats")
@Tag(name = "Presence")
@SecurityRequirement(name = "bearerAuth")
public class PresenceController {

    private final PresenceService presenceService;

    public PresenceController(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @GetMapping("/{chatId}/presence")
    public ResponseEntity<Set<UUID>> getPresence(@PathVariable UUID chatId) {
        return ResponseEntity.ok(presenceService.getOnlineMembers(chatId));
    }
}
