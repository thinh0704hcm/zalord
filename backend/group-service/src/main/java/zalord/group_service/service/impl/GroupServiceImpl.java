package zalord.group_service.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zalord.group_service.config.RabbitMQConfig;
import zalord.group_service.dto.event.GroupCreatedEvent;
import zalord.group_service.dto.event.GroupMemberAddedEvent;
import zalord.group_service.dto.event.GroupMemberRemovedEvent;
import zalord.group_service.dto.event.GroupUpdatedEvent;
import zalord.group_service.dto.request.AddMemberRequest;
import zalord.group_service.dto.request.CreateGroupRequest;
import zalord.group_service.dto.request.UpdateGroupRequest;
import zalord.group_service.dto.response.GroupMemberResponse;
import zalord.group_service.dto.response.GroupResponse;
import zalord.group_service.dto.response.PageResponse;
import zalord.group_service.enums.MemberRole;
import zalord.group_service.exception.*;
import zalord.group_service.model.Group;
import zalord.group_service.model.GroupMember;
import zalord.group_service.model.OutboxEvent;
import zalord.group_service.repository.GroupMemberRepository;
import zalord.group_service.repository.GroupRepository;
import zalord.group_service.repository.OutboxEventRepository;
import zalord.group_service.service.IGroupService;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class GroupServiceImpl implements IGroupService {

    private final GroupRepository groupRepo;
    private final GroupMemberRepository memberRepo;
    private final OutboxEventRepository outboxRepo;
    private final ObjectMapper objectMapper;

    public GroupServiceImpl(GroupRepository groupRepo,
                            GroupMemberRepository memberRepo,
                            OutboxEventRepository outboxRepo,
                            ObjectMapper objectMapper) {
        this.groupRepo = groupRepo;
        this.memberRepo = memberRepo;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public GroupResponse create(UUID caller, CreateGroupRequest req) {
        // Dedupe + remove caller from member list (caller is auto-OWNER below).
        Set<UUID> memberSet = new HashSet<>(req.memberIds());
        memberSet.remove(caller);
        if (memberSet.isEmpty()) {
            throw new InvalidRequestException("Group must have at least one OTHER member besides the creator");
        }

        Group g = new Group();
        g.setName(req.name());
        g.setAvatarUrl(req.avatarUrl());
        g.setCreatedBy(caller);
        g = groupRepo.save(g);

        addMemberRow(g.getId(), caller, MemberRole.OWNER);
        for (UUID uid : memberSet) addMemberRow(g.getId(), uid, MemberRole.MEMBER);

        // Event — message-service projects Conversation(id=groupId) + members.
        List<UUID> allMembers = new java.util.ArrayList<>(memberSet);
        allMembers.add(caller);
        enqueueEvent(RabbitMQConfig.GROUP_CREATED_ROUTING_KEY,
                new GroupCreatedEvent(g.getId(), g.getName(), caller, allMembers, g.getCreatedAt()));

        log.info("Group created id={} name='{}' members={}", g.getId(), g.getName(), allMembers.size());
        return loadGroupResponse(g);
    }

    @Override
    @Transactional(readOnly = true)
    public GroupResponse get(UUID caller, UUID groupId) {
        Group g = groupRepo.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found: " + groupId));
        requireMember(groupId, caller);
        return loadGroupResponse(g);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<GroupResponse> listMine(UUID caller, int page, int size) {
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 100) size = 100;

        Pageable pageable = PageRequest.of(page - 1, size);
        Page<UUID> groupIdsPage = memberRepo.findGroupIdsByUserId(caller, pageable);

        List<GroupResponse> items = groupIdsPage.getContent().stream()
                .map(id -> groupRepo.findById(id).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(this::loadGroupResponse)
                .toList();

        return PageResponse.of(items, page, size, groupIdsPage.getTotalElements());
    }

    @Override
    @Transactional
    public GroupResponse update(UUID caller, UUID groupId, UpdateGroupRequest req) {
        Group g = groupRepo.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found: " + groupId));
        requireRoleAtLeast(groupId, caller, MemberRole.ADMIN);

        boolean changed = false;
        if (req.name() != null && !req.name().isBlank()) {
            g.setName(req.name());
            changed = true;
        }
        if (req.avatarUrl() != null) {
            g.setAvatarUrl(req.avatarUrl().isEmpty() ? null : req.avatarUrl());
            changed = true;
        }
        if (!changed) {
            throw new InvalidRequestException("Nothing to update");
        }

        enqueueEvent(RabbitMQConfig.GROUP_UPDATED_ROUTING_KEY,
                new GroupUpdatedEvent(g.getId(), g.getName(), g.getAvatarUrl(), Instant.now()));

        log.info("Group updated id={}", g.getId());
        return loadGroupResponse(g);
    }

    @Override
    @Transactional
    public GroupResponse addMember(UUID caller, UUID groupId, AddMemberRequest req) {
        Group g = groupRepo.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found: " + groupId));
        requireRoleAtLeast(groupId, caller, MemberRole.ADMIN);

        // OWNER can't be assigned via this endpoint (only on create).
        MemberRole role = req.role() == null ? MemberRole.MEMBER : req.role();
        if (role == MemberRole.OWNER) {
            throw new InvalidRequestException("Cannot assign OWNER role through add-member");
        }

        if (memberRepo.existsByGroupIdAndUserId(groupId, req.userId())) {
            throw new MemberAlreadyExistsException("User is already a member of this group");
        }

        GroupMember m = addMemberRow(groupId, req.userId(), role);

        enqueueEvent(RabbitMQConfig.GROUP_MEMBER_ADDED_ROUTING_KEY,
                new GroupMemberAddedEvent(groupId, m.getUserId(), m.getJoinedAt()));

        log.info("Member added group={} user={} role={}", groupId, req.userId(), role);
        return loadGroupResponse(g);
    }

    @Override
    @Transactional
    public void removeMember(UUID caller, UUID groupId, UUID userId) {
        groupRepo.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found: " + groupId));

        GroupMember target = memberRepo.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new NotMemberException("Target user is not a member"));

        // Self-removal == leave (allowed). Otherwise need ADMIN+.
        if (!userId.equals(caller)) {
            requireRoleAtLeast(groupId, caller, MemberRole.ADMIN);
        }
        if (target.getRole() == MemberRole.OWNER) {
            throw new InvalidRequestException("OWNER cannot be removed; transfer ownership first");
        }

        memberRepo.deleteByGroupIdAndUserId(groupId, userId);

        enqueueEvent(RabbitMQConfig.GROUP_MEMBER_REMOVED_ROUTING_KEY,
                new GroupMemberRemovedEvent(groupId, userId, Instant.now()));

        log.info("Member removed group={} user={} byCaller={}", groupId, userId, caller);
    }

    @Override
    @Transactional
    public void leave(UUID caller, UUID groupId) {
        // Note: calls removeMember inline (self-invocation bypasses Spring's
        // @Transactional proxy), so leave() must declare its own @Transactional.
        removeMember(caller, groupId, caller);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private GroupMember addMemberRow(UUID groupId, UUID userId, MemberRole role) {
        GroupMember m = new GroupMember();
        m.setGroupId(groupId);
        m.setUserId(userId);
        m.setRole(role);
        return memberRepo.save(m);
    }

    private void requireMember(UUID groupId, UUID userId) {
        if (!memberRepo.existsByGroupIdAndUserId(groupId, userId)) {
            throw new NotMemberException("You are not a member of this group");
        }
    }

    private void requireRoleAtLeast(UUID groupId, UUID userId, MemberRole minRole) {
        GroupMember m = memberRepo.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new NotMemberException("You are not a member of this group"));
        // Role precedence: OWNER > ADMIN > MEMBER. Comparing ordinals is fine
        // because the enum declaration order matches that precedence.
        if (m.getRole().ordinal() > minRole.ordinal()) {
            throw new InsufficientRoleException("Requires role " + minRole + " or higher");
        }
    }

    private GroupResponse loadGroupResponse(Group g) {
        List<GroupMember> members = memberRepo.findAllByGroupId(g.getId());
        List<GroupMemberResponse> memberDtos = members.stream()
                .map(m -> GroupMemberResponse.builder()
                        .userId(m.getUserId())
                        .role(m.getRole())
                        .joinedAt(m.getJoinedAt())
                        .build())
                .toList();
        return GroupResponse.builder()
                .id(g.getId())
                .name(g.getName())
                .avatarUrl(g.getAvatarUrl())
                .createdBy(g.getCreatedBy())
                .createdAt(g.getCreatedAt())
                .members(memberDtos)
                .build();
    }

    private void enqueueEvent(String routingKey, Object event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize outbox payload", ex);
        }
        OutboxEvent o = new OutboxEvent();
        o.setTopicExchange(RabbitMQConfig.GROUP_EXCHANGE);
        o.setRoutingKey(routingKey);
        o.setPayload(payload);
        outboxRepo.save(o);
    }
}
