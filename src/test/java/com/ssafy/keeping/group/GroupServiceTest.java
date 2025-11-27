package com.ssafy.keeping.group;

import com.ssafy.keeping.domain.group.constant.RequestStatus;
import com.ssafy.keeping.domain.group.dto.GroupDisbandResponseDto;
import com.ssafy.keeping.domain.group.dto.GroupLeaveResponseDto;
import com.ssafy.keeping.domain.group.model.Group;
import com.ssafy.keeping.domain.group.model.GroupMember;
import com.ssafy.keeping.domain.group.repository.GroupAddRequestRepository;
import com.ssafy.keeping.domain.group.repository.GroupMemberRepository;
import com.ssafy.keeping.domain.group.repository.GroupRepository;
import com.ssafy.keeping.domain.group.service.GroupService;
import com.ssafy.keeping.domain.notification.service.NotificationService;
import com.ssafy.keeping.domain.user.customer.model.Customer;
import com.ssafy.keeping.domain.user.customer.repository.CustomerRepository;
import com.ssafy.keeping.domain.wallet.model.Wallet;
import com.ssafy.keeping.domain.wallet.repository.WalletRepository;
import com.ssafy.keeping.domain.wallet.repository.WalletStoreBalanceRepository;
import com.ssafy.keeping.domain.wallet.repository.WalletStoreLotRepository;
import com.ssafy.keeping.domain.wallet.service.WalletServiceHS;
import com.ssafy.keeping.global.exception.CustomException;
import jdk.jfr.Name;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @InjectMocks
    private GroupService groupService;

    @Mock private WalletServiceHS walletService;
    @Mock private CustomerRepository customerRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private GroupAddRequestRepository groupAddRequestRepository;
    @Mock private NotificationService notificationService;

    @Mock private WalletRepository walletRepository;
    @Mock private WalletStoreBalanceRepository balanceRepository;
    @Mock private WalletStoreLotRepository lotRepository;

    // ====== helpers ======
    private Group mockGroup(Long gid) {
        Group g = Group.builder().groupId(gid).groupName("g").groupDescription("d").groupCode("CODE").build();
        return g;
    }
    private GroupMember mockMember(Group g, Long cid, boolean leader) {
        Customer u = Customer.builder().customerId(cid).name("u"+cid).build();
        return GroupMember.builder().group(g).user(u).leader(leader).build();
    }
    private Wallet mockWallet(Long wid) {
        return Wallet.builder().walletId(wid).group(null).build();
    }

    // ====== leaveGroup ======
    @Test
    @Name("모임원이 모임 나가기 성공")
    void leaveGroup_nonLeader_success() {
        Long gid = 1L, uid = 10L;
        Group group = mockGroup(gid);
        GroupMember me = mockMember(group, uid, false);

        when(groupRepository.findById(gid)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findGroupMember(gid, uid)).thenReturn(Optional.of(me));
        when(walletService.settleShareToIndividual(group.getGroupId(), uid)).thenReturn(300L);
        when(walletService.getTotalIndividualBalance(uid)).thenReturn(1300L);
        when(groupMemberRepository.findMemberIdsByGroupId(gid)).thenReturn(List.of(20L, 30L));

        GroupLeaveResponseDto dto = groupService.leaveGroup(gid, uid);

        assertEquals(gid, dto.getGroupId());
        assertEquals(uid, dto.getCustomerId());
        assertEquals(300L, dto.getRefunded());
        assertEquals(1300L, dto.getIndivBalance());
        assertTrue(dto.getLeftAt().isBefore(LocalDateTime.now().plusSeconds(2)));

        verify(groupMemberRepository).delete(me);
        verify(notificationService, atLeastOnce()).sendToCustomer(eq(uid), any(), contains("환급"));
        verify(notificationService, atLeastOnce()).sendToCustomer(eq(20L), any(), anyString());
        verify(notificationService, atLeastOnce()).sendToCustomer(eq(30L), any(), anyString());
    }

    @Test
    @Name("모임장이 모임 나가기 실패 throws 확인")
    void leaveGroup_leader_throws() {
        Long gid = 1L, leaderId = 99L;
        Group group = mockGroup(gid);
        GroupMember leader = mockMember(group, leaderId, true);

        when(groupRepository.findById(gid)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findGroupMember(gid, leaderId)).thenReturn(Optional.of(leader));

        assertThrows(CustomException.class, () -> groupService.leaveGroup(gid, leaderId));
        verifyNoInteractions(walletService);
    }

    // ====== disbandGroup ======
    @Test
    @Name("모임장이 모임 해체 성공 및 각각 회수 성공")
    void disbandGroup_leader_success_and_refundEach() {
        Long gid = 1L, leaderId = 99L;
        Group group = mockGroup(gid);
        GroupMember leader = mockMember(group, leaderId, true);
        Wallet gw = mockWallet(777L);

        when(groupRepository.findById(gid)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findGroupMember(gid, leaderId)).thenReturn(Optional.of(leader));
        when(walletRepository.findByGroupId(gid)).thenReturn(Optional.of(gw));
        when(groupMemberRepository.findMemberIdsByGroupId(gid)).thenReturn(List.of(99L, 10L, 20L));

        Map<Long, Long> refunded = new LinkedHashMap<>();
        refunded.put(99L, 100L);
        refunded.put(10L, 200L);
        refunded.put(20L, 300L);
        when(walletService.settleAllMembersShare(eq(group.getGroupId()), anyList())).thenReturn(refunded);

        when(balanceRepository.sumByWalletIdForUpdate(777L)).thenReturn(Optional.of(0L));
        when(lotRepository.existsActiveLotByWalletId(777L)).thenReturn(false);

        GroupDisbandResponseDto dto = groupService.disbandGroup(gid, leaderId);

        assertEquals(gid, dto.groupId());
        assertEquals(3, dto.memberCount());
        assertEquals(600L, dto.totalRefunded());
        assertEquals(3, dto.refundedByMember().size());

        verify(lotRepository).deleteByWalletId(777L);
        verify(balanceRepository).deleteByWalletId(777L);
        verify(groupMemberRepository).deleteByGroupId(gid);
        verify(walletRepository).delete(gw);
        verify(groupRepository).delete(group);

        verify(notificationService, times(3))
                .sendToCustomer(anyLong(), any(), contains("환급"));
    }

    @Test
    @Name("모임원이 모임해체시 실패 throws 확인")
    void disbandGroup_nonLeader_throws() {
        Long gid = 1L, uid = 10L;
        Group group = mockGroup(gid);
        GroupMember mem = mockMember(group, uid, false);

        when(groupRepository.findById(gid)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findGroupMember(gid, uid)).thenReturn(Optional.of(mem));

        assertThrows(CustomException.class, () -> groupService.disbandGroup(gid, uid));
        verifyNoInteractions(walletService);
    }

    @Test
    @Name("모임장이 모임 해체시 일치하지않는 잔액 throws")
    void disbandGroup_inconsistentBalance_throws() {
        Long gid = 1L, leaderId = 99L;
        Group group = mockGroup(gid);
        GroupMember leader = mockMember(group, leaderId, true);
        Wallet gw = mockWallet(777L);

        when(groupRepository.findById(gid)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findGroupMember(gid, leaderId)).thenReturn(Optional.of(leader));
        when(walletRepository.findByGroupId(gid)).thenReturn(Optional.of(gw));
        when(groupMemberRepository.findMemberIdsByGroupId(gid)).thenReturn(List.of(99L));

        when(walletService.settleAllMembersShare(eq(group.getGroupId()), anyList()))
                .thenReturn(Map.of(99L, 100L));
        when(balanceRepository.sumByWalletIdForUpdate(777L)).thenReturn(Optional.of(10L)); // 남아있음

        assertThrows(CustomException.class, () -> groupService.disbandGroup(gid, leaderId));

        verify(lotRepository, never()).deleteByWalletId(anyLong());
        verify(balanceRepository, never()).deleteByWalletId(anyLong());
        verify(groupRepository, never()).delete(any());
    }
}