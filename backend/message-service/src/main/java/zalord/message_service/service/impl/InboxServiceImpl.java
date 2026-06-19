package zalord.message_service.service.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zalord.message_service.dto.response.InboxItemResponse;
import zalord.message_service.dto.response.PageResponse;
import zalord.message_service.model.ConversationView;
import zalord.message_service.repository.ConversationViewRepository;
import zalord.message_service.service.IInboxService;

import java.util.List;
import java.util.UUID;

@Service
public class InboxServiceImpl implements IInboxService {

    private final ConversationViewRepository viewRepo;

    public InboxServiceImpl(ConversationViewRepository viewRepo) {
        this.viewRepo = viewRepo;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InboxItemResponse> listInbox(UUID userId, int page, int size) {
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 100) size = 100;

        Pageable pageable = PageRequest.of(page - 1, size);
        Page<ConversationView> result = viewRepo.findByUserIdOrderByLastMessageAtDesc(userId, pageable);

        List<InboxItemResponse> items = result.getContent().stream()
                .map(v -> InboxItemResponse.builder()
                        .conversationId(v.getConversationId())
                        .otherUserId(v.getOtherUserId())
                        .lastMessagePreview(v.getLastMessagePreview())
                        .lastMessageAt(v.getLastMessageAt())
                        .lastSenderId(v.getLastSenderId())
                        .unreadCount(v.getUnreadCount() == null ? 0 : v.getUnreadCount())
                        .build())
                .toList();

        return PageResponse.of(items, page, size, result.getTotalElements());
    }

    @Override
    @Transactional
    public void markRead(UUID userId, UUID conversationId) {
        viewRepo.markRead(userId, conversationId);
    }
}
