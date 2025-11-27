package com.ssafy.keeping.group;

import com.ssafy.keeping.domain.group.model.Group;
import com.ssafy.keeping.domain.group.model.GroupMember;
import com.ssafy.keeping.domain.group.repository.GroupMemberRepository;
import com.ssafy.keeping.domain.group.repository.GroupRepository;
import com.ssafy.keeping.domain.group.service.GroupService;
import com.ssafy.keeping.domain.notification.entity.NotificationType;
import com.ssafy.keeping.domain.notification.service.NotificationService;
import com.ssafy.keeping.domain.user.customer.model.Customer;
import com.ssafy.keeping.domain.user.customer.repository.CustomerRepository;
import com.ssafy.keeping.domain.wallet.service.WalletServiceHS;
import com.ssafy.keeping.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupServiceExpelMemberUnitTest {

    @InjectMocks GroupService groupService; // 대상
    @Mock WalletServiceHS walletService;
    @Mock CustomerRepository customerRepository;
    @Mock GroupRepository groupRepository;
    @Mock GroupMemberRepository groupMemberRepository;
    @Mock NotificationService notificationService;

    private Group group(long id) {
        return Group.builder().groupId(id).groupName("G"+id).groupCode("GC"+id).build();
    }
    private Customer customer(long id) {
        return Customer.builder().customerId(id).name("U"+id).build();
    }
    private GroupMember member(Group g, Customer u, boolean leader) {
        return GroupMember.builder().group(g).user(u).leader(leader).build();
    }

    @Test
    @DisplayName("잔액>0 → 정산 호출, 멤버 삭제, 알림 1+N 호출")
    void expel_positiveRemain() {
        long groupId=1L, leaderId=10L, targetId=20L;
        Group g = group(groupId);
        GroupMember target = member(g, customer(targetId), false);

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(g));
        when(groupMemberRepository.existsLeader(groupId, leaderId)).thenReturn(true);
        when(groupMemberRepository.findGroupMember(groupId, targetId)).thenReturn(Optional.of(target));
        when(walletService.getMemberSharedBalance(g.getGroupId(), targetId)).thenReturn(100L);
        when(groupMemberRepository.findMemberIdsByGroupId(groupId)).thenReturn(List.of(leaderId, 30L));

        // afterCommit은 테스트에서 트랜잭션 동기화가 없으므로 즉시 실행됨 → 바로 검증 가능
        groupService.expelMember(groupId, leaderId, targetId);

        verify(walletService).settleShareToIndividual(g.getGroupId(), targetId);
        verify(groupMemberRepository).delete(target);

        verify(notificationService).sendToCustomer(targetId, NotificationType.MEMBER_EXPELLED, "모임에서 내보내졌습니다.");
        verify(notificationService).sendToCustomer(leaderId, NotificationType.MEMBER_EXPELLED, "모임원이 내보내졌습니다.");
        verify(notificationService).sendToCustomer(30L, NotificationType.MEMBER_EXPELLED, "모임원이 내보내졌습니다.");
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    @DisplayName("잔액=0 → 정산 없이 삭제와 알림만")
    void expel_zeroRemain() {
        long groupId=2L, leaderId=11L, targetId=21L;
        Group g = group(groupId);
        GroupMember target = member(g, customer(targetId), false);

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(g));
        when(groupMemberRepository.existsLeader(groupId, leaderId)).thenReturn(true);
        when(groupMemberRepository.findGroupMember(groupId, targetId)).thenReturn(Optional.of(target));
        when(walletService.getMemberSharedBalance(g.getGroupId(), targetId)).thenReturn(0L);
        when(groupMemberRepository.findMemberIdsByGroupId(groupId)).thenReturn(List.of(leaderId));

        groupService.expelMember(groupId, leaderId, targetId);

        verify(walletService, never()).settleShareToIndividual(any(), anyLong());
        verify(groupMemberRepository).delete(target);
        verify(notificationService, times(2))
                .sendToCustomer(anyLong(), eq(NotificationType.MEMBER_EXPELLED), anyString());
    }

    @Test
    @DisplayName("리더 아님 → ONLY_GROUP_LEADER")
    void expel_notLeader() {
        long groupId=3L, leaderId=12L, targetId=22L;

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group(groupId)));
        when(groupMemberRepository.existsLeader(groupId, leaderId)).thenReturn(false);

        assertThatThrownBy(() -> groupService.expelMember(groupId, leaderId, targetId))
                .isInstanceOf(CustomException.class);

        verify(groupMemberRepository, never()).delete(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("자기 자신 추방 시도 → BAD_REQUEST")
    void expel_self() {
        long groupId=4L, leaderId=13L;

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group(groupId)));
        when(groupMemberRepository.existsLeader(groupId, leaderId)).thenReturn(true);

        assertThatThrownBy(() -> groupService.expelMember(groupId, leaderId, leaderId))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("대상이 리더 → BAD_REQUEST")
    void expel_targetIsLeader() {
        long groupId=5L, leaderId=14L, targetId=24L;
        Group g = group(groupId);
        GroupMember targetLeader = member(g, customer(targetId), true);

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(g));
        when(groupMemberRepository.existsLeader(groupId, leaderId)).thenReturn(true);
        when(groupMemberRepository.findGroupMember(groupId, targetId)).thenReturn(Optional.of(targetLeader));

        assertThatThrownBy(() -> groupService.expelMember(groupId, leaderId, targetId))
                .isInstanceOf(CustomException.class);
    }
}