package com.ssafy.keeping.group;

import com.ssafy.keeping.domain.group.model.Group;
import com.ssafy.keeping.domain.group.repository.GroupRepository;
import com.ssafy.keeping.domain.payment.transactions.constant.TransactionType;
import com.ssafy.keeping.domain.payment.transactions.model.Transaction;
import com.ssafy.keeping.domain.payment.transactions.repository.TransactionRepository;
import com.ssafy.keeping.domain.store.constant.StoreStatus;
import com.ssafy.keeping.domain.store.model.Store;
import com.ssafy.keeping.domain.store.repository.StoreRepository;
import com.ssafy.keeping.domain.user.customer.model.Customer;
import com.ssafy.keeping.domain.user.customer.repository.CustomerRepository;
import com.ssafy.keeping.domain.user.owner.model.Owner;
import com.ssafy.keeping.domain.wallet.constant.LotSourceType;
import com.ssafy.keeping.domain.wallet.constant.LotStatus;
import com.ssafy.keeping.domain.wallet.constant.WalletType;
import com.ssafy.keeping.domain.wallet.model.Wallet;
import com.ssafy.keeping.domain.wallet.model.WalletStoreLot;
import com.ssafy.keeping.domain.wallet.repository.WalletRepository;
import com.ssafy.keeping.domain.wallet.repository.WalletStoreLotRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.ssafy.keeping.domain.auth.enums.AuthProvider.KAKAO;
import static com.ssafy.keeping.domain.auth.enums.Gender.MALE;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class WalletStoreLotRepositoryTest {

    @Autowired WalletStoreLotRepository lotRepo;
    @Autowired WalletRepository walletRepo;
    @Autowired CustomerRepository customerRepo;
    @Autowired GroupRepository groupRepo;
    @Autowired StoreRepository storeRepo;
    @Autowired TransactionRepository txRepo;
    @Autowired EntityManager em;

    // 공통 픽스처
    Customer target, other;
    Group g1, g2;
    Wallet groupW1, groupW2, targetW, otherW;
    Store store;

    @BeforeEach
    void setUp() {
        target = customerRepo.save(Customer.builder()
                .providerId("pid-target").providerType(KAKAO)
                .email("target@example.com").phoneNumber("010-0000-0001")
                .birth(LocalDate.of(1995,1,1)).name("타겟").gender(MALE)
                .imgUrl("https://img/1.png").userKey("UK1").build());
        other = customerRepo.save(Customer.builder()
                .providerId("pid-other").providerType(KAKAO)
                .email("other@example.com").phoneNumber("010-0000-0002")
                .birth(LocalDate.of(1994,1,1)).name("다른유저").gender(MALE)
                .imgUrl("https://img/2.png").userKey("UK2").build());

        g1 = groupRepo.save(Group.builder().groupName("그룹A").groupCode("GA-001").groupDescription("descA").build());
        g2 = groupRepo.save(Group.builder().groupName("그룹B").groupCode("GB-001").groupDescription("descB").build());

        groupW1 = walletRepo.save(Wallet.builder().group(g1).walletType(WalletType.GROUP).build());
        groupW2 = walletRepo.save(Wallet.builder().group(g2).walletType(WalletType.GROUP).build());
        targetW = walletRepo.save(Wallet.builder().customer(target).walletType(WalletType.INDIVIDUAL).build());
        otherW  = walletRepo.save(Wallet.builder().customer(other ).walletType(WalletType.INDIVIDUAL).build());

        Owner owner = Owner.builder()
                .providerId("owner-1").providerType(KAKAO)
                .email("owner@ex.com").phoneNumber("010-9999-9999")
                .birth(LocalDate.of(1990,1,1)).name("사장님").gender(MALE)
                .imgUrl("https://img/owner.png").userKey("OW1").build();
        em.persist(owner);

        store = storeRepo.save(Store.builder()
                .storeName("테스트가게").address("서울시 어딘가 1-1")
                .phoneNumber("02-000-0000").bankAccount("001-0000-000000")
                .category("FOOD").imgUrl("https://img/store.png")
                .taxIdNumber("123-45-67890").storeStatus(StoreStatus.ACTIVE)
                .owner(owner).merchantId(1L).build());
    }

    private Transaction charge(Wallet w, Customer c, long amount) {
        return txRepo.save(Transaction.builder()
                .wallet(w).customer(c).store(store)
                .transactionType(TransactionType.CHARGE).amount(amount)
                .createdAt(LocalDateTime.now()).build());
    }

    private WalletStoreLot lot(Wallet groupWallet, Wallet contributorWallet, long total, long remain,
                               LocalDateTime acq, LocalDateTime exp, Transaction originTx) {
        return lotRepo.save(WalletStoreLot.builder()
                .wallet(groupWallet).store(store)
                .amountTotal(total).amountRemaining(remain)
                .acquiredAt(acq).expiredAt(exp)
                .sourceType(LotSourceType.TRANSFER_IN)
                .contributorWallet(contributorWallet)
                .originChargeTransaction(originTx)
                .lotStatus(LotStatus.ACTIVE).build());
    }

    @Test
    @DisplayName("잔량>0, 미만료, 동일 그룹지갑, 동일 기여자만 반환")
    void filterActiveLotsByWalletAndContributor() {
        Transaction originTx = charge(targetW, target, 2_000L);

        // HIT
        lot(groupW1, targetW, 1_000L, 800L,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(7),
                originTx);

        // 제외 케이스들
        lot(groupW1, targetW, 500L, 0L,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(7),
                originTx); // 잔량 0
        lot(groupW1, targetW, 700L, 700L,
                LocalDateTime.now().minusDays(10),
                LocalDateTime.now().minusDays(1),
                originTx); // 만료
        lot(groupW1, otherW, 900L, 900L,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(7),
                originTx); // 다른 기여자
        lot(groupW2, targetW, 600L, 600L,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(7),
                originTx); // 다른 그룹지갑

        em.flush(); em.clear();

        List<WalletStoreLot> found =
                lotRepo.findActiveByWalletIdAndContributorCustomerId(groupW1.getWalletId(), target.getCustomerId());

        assertThat(found).hasSize(1);
        WalletStoreLot hit = found.get(0);
        assertThat(hit.getWallet().getWalletId()).isEqualTo(groupW1.getWalletId());
        assertThat(hit.getContributorWallet().getCustomer().getCustomerId()).isEqualTo(target.getCustomerId());
        assertThat(hit.getAmountRemaining()).isEqualTo(800L);
        assertThat(hit.getExpiredAt()).isAfter(LocalDateTime.now());
        assertThat(hit.getLotStatus()).isEqualTo(LotStatus.ACTIVE);
        assertThat(hit.getSourceType()).isEqualTo(LotSourceType.TRANSFER_IN);
    }

    @Test
    @DisplayName("기여 없음 또는 잔량=0/만료만 존재 → 활성 LOT 조회 결과 없음(추방 가능)")
    void noActiveLotsMeansRemovable() {
        // 기여 없음
        List<WalletStoreLot> none =
                lotRepo.findActiveByWalletIdAndContributorCustomerId(groupW1.getWalletId(), other.getCustomerId());
        assertThat(none).isEmpty();

        // 잔량 0 또는 만료만 존재
        Transaction tx = charge(targetW, target, 1_000L);
        lot(groupW1, targetW, 500L, 0L,
                LocalDateTime.now().minusDays(2),
                LocalDateTime.now().plusDays(7),
                tx); // 잔량 0
        lot(groupW1, targetW, 700L, 700L,
                LocalDateTime.now().minusDays(10),
                LocalDateTime.now().minusDays(1),
                tx); // 만료

        em.flush(); em.clear();

        List<WalletStoreLot> found =
                lotRepo.findActiveByWalletIdAndContributorCustomerId(groupW1.getWalletId(), target.getCustomerId());
        assertThat(found).isEmpty();
    }
}