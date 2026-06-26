package zalord.group_service.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zalord.group_service.dto.request.AddMemberRequest;
import zalord.group_service.dto.request.CreateGroupRequest;
import zalord.group_service.dto.response.GroupResponse;
import zalord.group_service.enums.MemberRole;
import zalord.group_service.exception.InvalidRequestException;
import zalord.group_service.exception.NotMemberException;
import zalord.group_service.model.Group;
import zalord.group_service.model.GroupMember;
import zalord.group_service.repository.GroupMemberRepository;
import zalord.group_service.repository.GroupRepository;
import zalord.group_service.repository.OutboxEventRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupServiceImplTest {

    @Mock
    private GroupRepository groupRepo;
    @Mock
    private GroupMemberRepository memberRepo;
    @Mock
    private OutboxEventRepository outboxRepo;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private GroupServiceImpl groupService;

    private UUID callerId;
    private UUID otherUserId;
    private UUID groupId;
    private Group testGroup;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        callerId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        groupId = UUID.randomUUID();
        
        testGroup = new Group();
        testGroup.setId(groupId);
        testGroup.setName("Test Group");
        testGroup.setCreatedBy(callerId);
        
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    @Test
    void create_Success() {
        CreateGroupRequest req = new CreateGroupRequest("Test Group", null, List.of(otherUserId));
        when(groupRepo.save(any(Group.class))).thenReturn(testGroup);
        
        GroupMember ownerMember = new GroupMember();
        ownerMember.setUserId(callerId);
        ownerMember.setRole(MemberRole.OWNER);
        
        GroupMember otherMember = new GroupMember();
        otherMember.setUserId(otherUserId);
        otherMember.setRole(MemberRole.MEMBER);
        
        when(memberRepo.save(any(GroupMember.class))).thenAnswer(i -> i.getArgument(0));
        when(memberRepo.findAllByGroupId(groupId)).thenReturn(List.of(ownerMember, otherMember));

        GroupResponse response = groupService.create(callerId, req);

        assertNotNull(response);
        assertEquals("Test Group", response.getName());
        assertEquals(2, response.getMembers().size());
        
        verify(groupRepo).save(any(Group.class));
        verify(memberRepo, times(2)).save(any(GroupMember.class));
        verify(outboxRepo).save(any());
    }

    @Test
    void create_Failure_NoOtherMembers() {
        CreateGroupRequest req = new CreateGroupRequest("Test Group", null, List.of(callerId)); // Only caller

        assertThrows(InvalidRequestException.class, () -> {
            groupService.create(callerId, req);
        });

        verify(groupRepo, never()).save(any(Group.class));
    }

    @Test
    void addMember_Success() {
        AddMemberRequest req = new AddMemberRequest(otherUserId, MemberRole.MEMBER);
        
        when(groupRepo.findById(groupId)).thenReturn(Optional.of(testGroup));
        
        GroupMember callerMember = new GroupMember();
        callerMember.setUserId(callerId);
        callerMember.setRole(MemberRole.ADMIN);
        when(memberRepo.findByGroupIdAndUserId(groupId, callerId)).thenReturn(Optional.of(callerMember));
        
        when(memberRepo.existsByGroupIdAndUserId(groupId, otherUserId)).thenReturn(false);
        when(memberRepo.save(any(GroupMember.class))).thenAnswer(i -> i.getArgument(0));

        GroupResponse response = groupService.addMember(callerId, groupId, req);

        assertNotNull(response);
        verify(memberRepo).save(any(GroupMember.class));
        verify(outboxRepo).save(any());
    }

    @Test
    void removeMember_Failure_NotAdmin() {
        when(groupRepo.findById(groupId)).thenReturn(Optional.of(testGroup));
        
        GroupMember targetMember = new GroupMember();
        targetMember.setUserId(otherUserId);
        targetMember.setRole(MemberRole.MEMBER);
        when(memberRepo.findByGroupIdAndUserId(groupId, otherUserId)).thenReturn(Optional.of(targetMember));
        
        GroupMember callerMember = new GroupMember();
        callerMember.setUserId(callerId);
        callerMember.setRole(MemberRole.MEMBER); // Only MEMBER, not ADMIN
        when(memberRepo.findByGroupIdAndUserId(groupId, callerId)).thenReturn(Optional.of(callerMember));

        assertThrows(Exception.class, () -> {
            groupService.removeMember(callerId, groupId, otherUserId);
        });

        verify(memberRepo, never()).deleteByGroupIdAndUserId(any(), any());
    }
}
