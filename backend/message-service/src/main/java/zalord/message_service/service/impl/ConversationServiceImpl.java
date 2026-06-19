package zalord.message_service.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zalord.message_service.dto.request.CreateConversationRequest;
import zalord.message_service.dto.response.ConversationResponse;
import zalord.message_service.dto.response.PageResponse;
import zalord.message_service.enums.ConversationType;
import zalord.message_service.exception.ConversationNotFoundException;
import zalord.message_service.exception.InvalidRequestException;
import zalord.message_service.exception.NotMemberException;
import zalord.message_service.model.Conversation;
import zalord.message_service.model.ConversationMember;
import zalord.message_service.model.DirectLookup;
import zalord.message_service.repository.ConversationMemberRepository;
import zalord.message_service.repository.ConversationRepository;
import zalord.message_service.repository.ConversationViewRepository;
import zalord.message_service.repository.DirectLookupRepository;
import zalord.message_service.service.IConversationService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class ConversationServiceImpl implements IConversationService {

    private final ConversationRepository convRepo;
    private final ConversationMemberRepository memberRepo;
    private final DirectLookupRepository directRepo;
    private final ConversationViewRepository viewRepo;

    public ConversationServiceImpl(ConversationRepository convRepo,
                                   ConversationMemberRepository memberRepo,
                                   DirectLookupRepository directRepo,
                                   ConversationViewRepository viewRepo) {
        this.convRepo = convRepo;
        this.memberRepo = memberRepo;
        this.directRepo = directRepo;
        this.viewRepo = viewRepo;
    }

    @Override
    @Transactional
    public ConversationResponse create(UUID caller, CreateConversationRequest req) {
        if (req.type() != ConversationType.DIRECT) {
            throw new InvalidRequestException("Only DIRECT conversations are supported in this version");
        }
        if (caller.equals(req.memberUserId())) {
            throw new InvalidRequestException("Cannot start a DIRECT conversation with yourself");
        }

        // Idempotent: same pair → same conversation.
        String key = DirectLookup.pairKeyOf(caller, req.memberUserId());
        Optional<DirectLookup> existing = directRepo.findById(key);
        if (existing.isPresent()) {
            UUID convId = existing.get().getConversationId();
            log.debug("DIRECT conv already exists for pair, returning convId={}", convId);
            // Defensive: ensure read-model rows exist even for legacy conversations
            // created before the CQRS read model was added.
            viewRepo.initView(caller, convId, req.memberUserId());
            viewRepo.initView(req.memberUserId(), convId, caller);
            return toResponse(convRepo.findById(convId).orElseThrow(
                    () -> new ConversationNotFoundException("Conversation referenced by direct_lookup missing: " + convId)),
                    List.of(caller, req.memberUserId()));
        }

        // Create new.
        Conversation conv = new Conversation();
        conv.setType(ConversationType.DIRECT);
        conv = convRepo.save(conv);

        addMember(conv.getId(), caller);
        addMember(conv.getId(), req.memberUserId());

        DirectLookup lookup = new DirectLookup();
        lookup.setPairKey(key);
        lookup.setConversationId(conv.getId());
        directRepo.save(lookup);

        // CQRS: seed empty read-model rows so the conv shows up in inbox right
        // away, even before any message. The projector will fill in preview +
        // unread later when messages arrive.
        viewRepo.initView(caller, conv.getId(), req.memberUserId());
        viewRepo.initView(req.memberUserId(), conv.getId(), caller);

        log.info("DIRECT conversation created id={} between {} and {}", conv.getId(), caller, req.memberUserId());
        return toResponse(conv, List.of(caller, req.memberUserId()));
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationResponse get(UUID caller, UUID conversationId) {
        Conversation conv = convRepo.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException("Conversation not found: " + conversationId));
        List<UUID> members = memberRepo.findAllByConversationId(conversationId).stream()
                .map(ConversationMember::getUserId)
                .toList();
        if (!members.contains(caller)) {
            throw new NotMemberException("You are not a member of this conversation");
        }
        return toResponse(conv, members);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ConversationResponse> listMine(UUID caller, int page, int size) {
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 100) size = 100;
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "joinedAt"));

        Page<UUID> convIdsPage = memberRepo.findConversationIdsByUserId(caller, pageable);
        List<UUID> convIds = convIdsPage.getContent();

        List<ConversationResponse> items = convIds.stream()
                .map(id -> {
                    Conversation conv = convRepo.findById(id).orElse(null);
                    if (conv == null) return null;
                    List<UUID> members = memberRepo.findAllByConversationId(id).stream()
                            .map(ConversationMember::getUserId)
                            .toList();
                    return toResponse(conv, members);
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        return PageResponse.of(items, page, size, convIdsPage.getTotalElements());
    }

    private void addMember(UUID conversationId, UUID userId) {
        ConversationMember m = new ConversationMember();
        m.setConversationId(conversationId);
        m.setUserId(userId);
        memberRepo.save(m);
    }

    private ConversationResponse toResponse(Conversation c, List<UUID> members) {
        return ConversationResponse.builder()
                .id(c.getId())
                .type(c.getType())
                .memberIds(members)
                .createdAt(c.getCreatedAt())
                .build();
    }
}
