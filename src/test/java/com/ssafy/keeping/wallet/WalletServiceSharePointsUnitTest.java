package com.ssafy.keeping.wallet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.keeping.domain.group.model.Group;
import com.ssafy.keeping.domain.group.repository.GroupMemberRepository;
import com.ssafy.keeping.domain.group.repository.GroupRepository;
import com.ssafy.keeping.domain.idempotency.constant.IdemActorType;
import com.ssafy.keeping.domain.idempotency.constant.IdemStatus;
import com.ssafy.keeping.domain.idempotency.dto.IdemBegin;
import com.ssafy.keeping.domain.idempotency.model.IdempotencyKey;
import com.ssafy.keeping.domain.idempotency.model.IdempotentResult;
import com.ssafy.keeping.domain.idempotency.service.IdempotencyService;
import com.ssafy.keeping.domain.payment.transactions.model.Transaction;
import com.ssafy.keeping.domain.payment.transactions.repository.TransactionRepository;
import com.ssafy.keeping.domain.store.model.Store;
import com.ssafy.keeping.domain.store.repository.StoreRepository;
import com.ssafy.keeping.domain.user.customer.model.Customer;
import com.ssafy.keeping.domain.user.customer.repository.CustomerRepository;
import com.ssafy.keeping.domain.wallet.constant.LotSourceType;
import com.ssafy.keeping.domain.wallet.constant.WalletType;
import com.ssafy.keeping.domain.wallet.dto.PointShareRequestDto;
import com.ssafy.keeping.domain.wallet.dto.PointShareResponseDto;
import com.ssafy.keeping.domain.wallet.model.Wallet;
import com.ssafy.keeping.domain.wallet.model.WalletStoreBalance;
import com.ssafy.keeping.domain.wallet.model.WalletStoreLot;
import com.ssafy.keeping.domain.wallet.repository.WalletRepository;
import com.ssafy.keeping.domain.wallet.repository.WalletStoreBalanceRepository;
import com.ssafy.keeping.domain.wallet.repository.WalletStoreLotRepository;
import com.ssafy.keeping.domain.wallet.service.WalletServiceHS;
import com.ssafy.keeping.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceSharePointsUnitTest {

    @InjectMocks WalletServiceHS walletService;

    @Mock StoreRepository storeRepository;
    @Mock WalletRepository walletRepository;
    @Mock WalletStoreBalanceRepository balanceRepository;
    @Mock GroupRepository groupRepository;
    @Mock CustomerRepository customerRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock WalletStoreLotRepository lotRepository;
    @Mock GroupMemberRepository groupMemberRepository;

    @Mock IdempotencyService idempotencyService;

    // NPE 방지: 실제 ObjectMapper 사용
    @Spy
    ObjectMapper canonicalObjectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private Customer customer(long id) { return Customer.builder().customerId(id).name("U"+id).build(); }
    private Group group(long id) { return Group.builder().groupId(id).groupName("G"+id).groupCode("GC"+id).build(); }
    private Wallet indiv(Customer c) { return Wallet.builder().walletId(101L).walletType(WalletType.INDIVIDUAL).customer(c).build(); }
    private Wallet gwallet(Group g) { return Wallet.builder().walletId(201L).walletType(WalletType.GROUP).group(g).build(); }
    private Store store(long id) { return Store.builder().storeId(id).storeName("S"+id).build(); }

    // ---------- 기존 성공 케이스(첫 호출 → Created) ----------
    @Test
    @DisplayName("sharePoints(idem): 첫 호출 → 201 Created, 이동·적립·거래 2건")
    void sharePoints_success_created() {
        long groupId = 1L, userId = 10L, storeId = 1000L;
        String idemKey = UUID.randomUUID().toString();
        var user = customer(userId);
        var g = group(groupId);
        var iw = indiv(user);
        var gw = gwallet(g);
        var s = store(storeId);

        var indivBal = WalletStoreBalance.builder().balanceId(1L).wallet(iw).store(s).balance(2_000L).build();
        var groupBal = WalletStoreBalance.builder().balanceId(2L).wallet(gw).store(s).balance(100L).build();

        var originChargeTx = mock(Transaction.class);
        when(originChargeTx.getTransactionId()).thenReturn(999L);
        var srcLot = WalletStoreLot.builder()
                .wallet(iw).store(s)
                .amountTotal(2_000L).amountRemaining(2_000L)
                .acquiredAt(LocalDateTime.now().minusDays(1))
                .expiredAt(LocalDateTime.now().plusDays(30))
                .sourceType(LotSourceType.TRANSFER_IN)
                .contributorWallet(iw)
                .originChargeTransaction(originChargeTx)
                .build();

        when(customerRepository.findById(userId)).thenReturn(Optional.of(user));
        when(walletRepository.findById(iw.getWalletId())).thenReturn(Optional.of(iw));
        when(walletRepository.findById(gw.getWalletId())).thenReturn(Optional.of(gw));
        when(groupMemberRepository.existsMember(groupId, userId)).thenReturn(true);
        when(storeRepository.findById(storeId)).thenReturn(Optional.of(s));
        when(balanceRepository.lockByWalletIdAndStoreId(iw.getWalletId(), storeId)).thenReturn(Optional.of(indivBal));
        when(balanceRepository.lockByWalletIdAndStoreId(gw.getWalletId(), storeId)).thenReturn(Optional.of(groupBal));
        when(lotRepository.lockAllByWalletIdAndStoreIdOrderByAcquiredAt(iw.getWalletId(), storeId)).thenReturn(List.of(srcLot));
        when(lotRepository.findByWalletIdAndStoreIdAndOriginChargeTxIdAndSourceType(gw.getWalletId(), storeId, 999L, LotSourceType.TRANSFER_IN))
                .thenReturn(Optional.empty());
        when(lotRepository.save(any(WalletStoreLot.class))).thenAnswer((Answer<WalletStoreLot>) inv -> inv.getArgument(0));

        Transaction txOut = mock(Transaction.class);
        Transaction txIn  = mock(Transaction.class);
        when(txOut.getTransactionId()).thenReturn(5000L);
        when(txIn.getTransactionId()).thenReturn(6000L);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(txOut, txIn);

        // 멱등: 새 생성(IN_PROGRESS)
        IdempotencyKey row = mock(IdempotencyKey.class);
        when(row.getStatus()).thenReturn(IdemStatus.IN_PROGRESS);
        IdemBegin begin = new IdemBegin(row, true);
        when(idempotencyService.beginOrLoad(eq(IdemActorType.CUSTOMER), eq(userId), eq("POST"),
                eq("/groups/" + groupId + "/stores/" + storeId), any(UUID.class), any()))
                .thenReturn(begin);
        when(idempotencyService.isBodyConflict(any(), any())).thenReturn(false);
        doNothing().when(idempotencyService).completeCharge(any(), anyInt(), any());

        PointShareRequestDto req = new PointShareRequestDto();
        req.setIndividualWalletId(iw.getWalletId());
        req.setGroupWalletId(gw.getWalletId());
        req.setShareAmount(800L);

        IdempotentResult<PointShareResponseDto> result =
                walletService.sharePoints(groupId, userId, storeId, idemKey, req);

        assertThat(result.getHttpStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.isReplay()).isFalse();
        PointShareResponseDto res = result.getBody();
        assertThat(res.txOutId()).isEqualTo(5000L);
        assertThat(res.txInId()).isEqualTo(6000L);
        assertThat(indivBal.getBalance()).isEqualTo(1_200L);
        assertThat(groupBal.getBalance()).isEqualTo(900L);
        assertThat(srcLot.getAmountRemaining()).isEqualTo(1_200L);

        verify(idempotencyService).completeCharge(any(), eq(201), any());
    }

    // ---------- 잔액 부족 ----------
    @Test
    @DisplayName("sharePoints(idem): 개인 잔액 부족 → 예외, 스냅샷 기록 안 함")
    void sharePoints_insufficient() {
        long groupId = 1L, userId = 10L, storeId = 1000L;
        String idemKey = UUID.randomUUID().toString();

        var user = customer(userId);
        var g = group(groupId);
        var iw = indiv(user);
        var gw = gwallet(g);
        var s = store(storeId);

        when(customerRepository.findById(userId)).thenReturn(Optional.of(user));
        when(walletRepository.findById(iw.getWalletId())).thenReturn(Optional.of(iw));
        when(walletRepository.findById(gw.getWalletId())).thenReturn(Optional.of(gw));
        when(groupMemberRepository.existsMember(groupId, userId)).thenReturn(true);
        when(storeRepository.findById(storeId)).thenReturn(Optional.of(s));

        var indivBal = WalletStoreBalance.builder().wallet(iw).store(s).balance(100L).build();
        when(balanceRepository.lockByWalletIdAndStoreId(iw.getWalletId(), storeId)).thenReturn(Optional.of(indivBal));

        IdempotencyKey row = mock(IdempotencyKey.class);
        when(row.getStatus()).thenReturn(IdemStatus.IN_PROGRESS);
        IdemBegin begin = new IdemBegin(row, true);
        when(idempotencyService.beginOrLoad(eq(IdemActorType.CUSTOMER), eq(userId), eq("POST"),
                eq("/groups/" + groupId + "/stores/" + storeId), any(UUID.class), any()))
                .thenReturn(begin);
        when(idempotencyService.isBodyConflict(any(), any())).thenReturn(false);

        PointShareRequestDto req = new PointShareRequestDto();
        req.setIndividualWalletId(iw.getWalletId());
        req.setGroupWalletId(gw.getWalletId());
        req.setShareAmount(800L);

        assertThatThrownBy(() -> walletService.sharePoints(groupId, userId, storeId, idemKey, req))
                .isInstanceOf(CustomException.class);

        verify(transactionRepository, never()).save(any());
        verify(lotRepository, never()).save(any());
        verify(idempotencyService, never()).completeCharge(any(), anyInt(), any());
    }

    // ---------- 재호출(Replay) ----------
    @Test
    @DisplayName("sharePoints(idem): 동일 키+본문 재호출 → 200 OK, isReplay=true, DB write 없음")
    void sharePoints_replay_ok() {
        long groupId = 1L, userId = 10L, storeId = 1000L;
        String idemKey = UUID.randomUUID().toString();

        PointShareResponseDto snapDto = new PointShareResponseDto(
                5000L, 6000L, 101L, 201L, storeId, 800L, 900L, 1200L, LocalDateTime.now(), true);
        JsonNode snap = canonicalObjectMapper.valueToTree(snapDto);

        IdempotencyKey row = mock(IdempotencyKey.class);
        when(row.getStatus()).thenReturn(IdemStatus.DONE);
        when(row.getResponseJson()).thenReturn(snap);
        IdemBegin begin = new IdemBegin(row, false);

        when(idempotencyService.beginOrLoad(eq(IdemActorType.CUSTOMER), eq(userId), eq("POST"),
                eq("/groups/" + groupId + "/stores/" + storeId), any(UUID.class), any()))
                .thenReturn(begin);
        when(idempotencyService.isBodyConflict(any(), any())).thenReturn(false);

        PointShareRequestDto req = new PointShareRequestDto();
        req.setIndividualWalletId(101L);
        req.setGroupWalletId(201L);
        req.setShareAmount(800L);

        IdempotentResult<PointShareResponseDto> result =
                walletService.sharePoints(groupId, userId, storeId, idemKey, req);

        assertThat(result.getHttpStatus()).isEqualTo(HttpStatus.OK);
        assertThat(result.isReplay()).isTrue();
        assertThat(result.getBody()).usingRecursiveComparison().isEqualTo(snapDto);

        verifyNoInteractions(transactionRepository, lotRepository, balanceRepository);
        verify(idempotencyService, never()).completeCharge(any(), anyInt(), any());
    }

    // ---------- 타 트랜잭션이 선점 중(IN_PROGRESS) ----------
    @Test
    @DisplayName("sharePoints(idem): IN_PROGRESS 선점 중 → 202 Accepted + Retry-After")
    void sharePoints_inProgress_accepted() {
        long groupId = 1L, userId = 10L, storeId = 1000L;
        String idemKey = UUID.randomUUID().toString();

        IdempotencyKey row = mock(IdempotencyKey.class);
        when(row.getStatus()).thenReturn(IdemStatus.IN_PROGRESS);
        IdemBegin begin = new IdemBegin(row, false); // created=false + IN_PROGRESS → 202

        when(idempotencyService.beginOrLoad(eq(IdemActorType.CUSTOMER), eq(userId), eq("POST"),
                eq("/groups/" + groupId + "/stores/" + storeId), any(UUID.class), any()))
                .thenReturn(begin);
        when(idempotencyService.isBodyConflict(any(), any())).thenReturn(false);

        PointShareRequestDto req = new PointShareRequestDto();
        req.setIndividualWalletId(101L);
        req.setGroupWalletId(201L);
        req.setShareAmount(800L);

        IdempotentResult<PointShareResponseDto> result =
                walletService.sharePoints(groupId, userId, storeId, idemKey, req);

        assertThat(result.getHttpStatus()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(result.getRetryAfterSeconds()).isNotNull();
        verifyNoInteractions(transactionRepository, lotRepository, balanceRepository);
        verify(idempotencyService, never()).completeCharge(any(), anyInt(), any());
    }

    // ---------- 동일 키 + 다른 바디 → 충돌 ----------
    @Test
    @DisplayName("sharePoints(idem): 동일 키에 다른 바디 → BODY_CONFLICT 예외")
    void sharePoints_bodyConflict() {
        long groupId = 1L, userId = 10L, storeId = 1000L;
        String idemKey = UUID.randomUUID().toString();

        IdempotencyKey row = mock(IdempotencyKey.class);
        IdemBegin begin = new IdemBegin(row, true);

        lenient().when(idempotencyService.beginOrLoad(
                        eq(IdemActorType.CUSTOMER), eq(userId), eq("POST"),
                        eq("/groups/" + groupId + "/stores/" + storeId), any(UUID.class), any()))
                .thenReturn(begin);

        lenient().when(idempotencyService.isBodyConflict(any(), any()))
                .thenReturn(true);

        PointShareRequestDto req = new PointShareRequestDto();
        req.setIndividualWalletId(101L);
        req.setGroupWalletId(201L);
        req.setShareAmount(800L);

        assertThatThrownBy(() -> walletService.sharePoints(groupId, userId, storeId, idemKey, req))
                .isInstanceOf(CustomException.class);

        verifyNoInteractions(transactionRepository, lotRepository, balanceRepository);
        verify(idempotencyService, never()).completeCharge(any(), anyInt(), any());
    }
}