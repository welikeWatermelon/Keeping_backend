package com.ssafy.keeping.domain.charge.service;

import com.ssafy.keeping.domain.charge.dto.ssafyapi.response.SsafyAccountDepositResponseDto;
import com.ssafy.keeping.domain.charge.model.SettlementTask;
import com.ssafy.keeping.domain.charge.repository.SettlementTaskRepository;
import com.ssafy.keeping.domain.user.owner.repository.OwnerRepository;
import com.ssafy.keeping.domain.store.model.Store;
import com.ssafy.keeping.domain.store.repository.StoreRepository;
import com.ssafy.keeping.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementScheduler {

    private final SsafyFinanceApiService ssafyFinanceApiService;
    private final SettlementTaskRepository settlementTaskRepository;
    private final StoreRepository storeRepository;
    private final OwnerRepository ownerRepository;

    /**
     * 매주 월요일 오전 07:30에 이전 주 PENDING 작업들을 LOCKED 상태로 변경
     * 싸피은행 청구서 발행에 맞춰 결제취소 불가 상태로 전환
     * cron 표현식: "0 30 7 * * MON" = 매주 월요일 오전 7시 30분 0초
     */
    @Scheduled(cron = "0 30 7 * * MON", zone = "Asia/Seoul")
    @Transactional
    public void lockPreviousWeekTasks() {
        log.info("=== 결제취소 차단 스케줄러 시작 ===");
        
        try {
            // 1. 청구서 발행 주기 범위 계산 (지난주 월요일 07:30 ~ 이번주 월요일 07:30)
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime thisWeekMondayBilling = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .withHour(7).withMinute(30).withSecond(0).withNano(0);
            LocalDateTime lastWeekMondayBilling = thisWeekMondayBilling.minusWeeks(1);
            
//            log.info("청구서 발행 주기 범위: {} ~ {}", lastWeekMondayBilling, thisWeekMondayBilling);
            log.info("청구서 발행 주기 범위: {} ~ {}", lastWeekMondayBilling, thisWeekMondayBilling);

            // 2. 해당 범위의 PENDING 상태 작업 조회
            List<SettlementTask> pendingTasks = settlementTaskRepository
                    .findPendingTasksFromPreviousWeek(lastWeekMondayBilling, thisWeekMondayBilling);


            if (pendingTasks.isEmpty()) {
                log.info("LOCKED로 변경할 작업이 없습니다.");
                return;
            }
            
            log.info("LOCKED로 변경할 작업 수: {}", pendingTasks.size());
            
            // 3. PENDING → LOCKED 상태 변경
            for (SettlementTask task : pendingTasks) {
                task.markAsLocked();
                settlementTaskRepository.save(task);
            }
            
            log.info("=== 결제취소 차단 스케줄러 완료 - 변경된 작업 수: {} ===", pendingTasks.size());
            
        } catch (Exception e) {
            log.error("결제취소 차단 스케줄러 실행 중 오류 발생", e);
        }
    }

    /**
     * 매주 화요일 오전 01:00에 LOCKED 상태 작업들에 대해 점주 정산 처리
     * cron 표현식: "0 0 1 * * TUE" = 매주 화요일 오전 1시 0분 0초
     */
    @Scheduled(cron = "0 0 1 * * TUE", zone = "Asia/Seoul")
    @Transactional
    public void processLockedSettlements() {
        log.info("=== 정산 처리 스케줄러 시작 ===");
        
        try {
            // 1. LOCKED 상태 정산 작업 조회
            List<SettlementTask> lockedTasks = settlementTaskRepository.findLockedTasks();
            
            if (lockedTasks.isEmpty()) {
                log.info("처리할 정산 작업이 없습니다.");
                return;
            }
            
            log.info("처리 대상 정산 작업 수: {}", lockedTasks.size());
            
            // 2. 가게별로 그룹화하여 정산
            Map<Store, List<SettlementTask>> tasksByStore = lockedTasks.stream()
                    .collect(Collectors.groupingBy(task -> task.getTransaction().getStore()));
            
            // 3. 각 가게별로 정산 처리
            for (Map.Entry<Store, List<SettlementTask>> entry : tasksByStore.entrySet()) {
                processStoreSettlement(entry.getKey(), entry.getValue());
            }
            
            log.info("=== 정산 처리 스케줄러 완료 ===");
            
        } catch (Exception e) {
            log.error("정산 처리 스케줄러 실행 중 오류 발생", e);
        }
    }

    /**
     * 특정 가게의 정산 처리
     */
    private void processStoreSettlement(Store store, List<SettlementTask> tasks) {
        try {
            log.info("가게 정산 처리 시작 - 가게: {}, 작업 수: {}", store.getStoreName(), tasks.size());
            
            // 1. 정산 금액 계산 (실제 결제금액 사용)
            Long totalAmount = tasks.stream()
                    .map(SettlementTask::getActualPaymentAmount) // 실제 결제금액 합산
                    .reduce(0L, Long::sum);
            
            if (totalAmount <= 0) {
                log.warn("정산 금액이 0 이하입니다. 가게: {}, 금액: {}", store.getStoreName(), totalAmount);
                return;
            }
            
//            // 2. 점주 정보 조회 - 연관관계 활용
//            Owner owner = store.getOwner();
//
//            if (owner == null) {
//                log.error("가게에 연결된 점주가 없습니다. 가게 ID: {}", store.getStoreId());
//                markTasksAsFailed(tasks, "가게에 연결된 점주가 없습니다.");
//                return;
//            }
//
//            if (owner.getUserKey() == null || owner.getUserKey().trim().isEmpty()) {
//                log.error("점주의 userKey가 없습니다. 점주 ID: {}", owner.getOwnerId());
//                markTasksAsFailed(tasks, "점주의 SSAFY 은행 계정이 없습니다.");
//                return;
//            }
//
            // 3. 외부 API 호출 (계좌 입금) - CustomException이 자동으로 던져짐 (SsafyFinanceApiService 에서 알아서 예외 처리)
            String transactionSummary = String.format("정산 입금 - %s", store.getStoreName());
            SsafyAccountDepositResponseDto response = ssafyFinanceApiService.requestAccountDeposit(
//                    owner.getUserKey(),
                    "현재는 임의",
                    store.getBankAccount(),
                    totalAmount,
                    transactionSummary
            );
            
            // 4. 정산 완료 처리
            markTasksAsCompleted(tasks);
            log.info("가게 정산 완료 - 가게: {}, 실제결제금액: {}, 거래번호: {}",
                    store.getStoreName(), totalAmount, response.getRec().getTransactionUniqueNo());
            
        } catch (CustomException e) {
            log.error("가게 정산 처리 중 비즈니스 오류 - 가게: {}, 에러: {}", store.getStoreName(), e.getMessage());
            markTasksAsFailed(tasks, e.getMessage());
        } catch (Exception e) {
            log.error("가게 정산 처리 중 시스템 오류 - 가게: {}", store.getStoreName(), e);
            markTasksAsFailed(tasks, "정산 처리 중 시스템 오류 발생");
        }
    }

    /**
     * 정산 작업들을 완료 상태로 변경
     */
    private void markTasksAsCompleted(List<SettlementTask> tasks) {
        for (SettlementTask task : tasks) {
            task.markAsCompleted();
            settlementTaskRepository.save(task);
        }
    }

    /**
     * 정산 작업들을 실패 상태로 변경
     */
    private void markTasksAsFailed(List<SettlementTask> tasks, String reason) {
        for (SettlementTask task : tasks) {
            task.markAsFailed();
            settlementTaskRepository.save(task);
        }
        log.error("정산 작업 실패 처리 완료. 작업 수: {}, 사유: {}", tasks.size(), reason);
    }
}