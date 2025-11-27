package com.ssafy.keeping.domain.event.service;

import com.ssafy.keeping.domain.event.dto.CancelEvent;
import com.ssafy.keeping.domain.event.dto.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private String notificationTopic = "notification-events"; // 토픽명

    private String anomalyDetectionTopic = "anomaly-detection-events"; // 토픽명

    /**
     * 카드 결제 완료 이벤트 발행
     */
    public void publishPaymentEvent(PaymentEvent event) {
        try {
            event.setEventType("PAYMENT");
            log.info("카드 결제 이벤트 발행 시작 - 고객ID: {}, 거래ID: {}, 결제금액: {}",
                    event.getCustomerId(), event.getTransactionId(), event.getPaymentAmount());

            // 알림용 토픽으로 발행
            CompletableFuture<SendResult<String, Object>> notificationResult =
                kafkaTemplate.send(notificationTopic, event.getCustomerId().toString(), event);
//                kafkaTemplate.send(토픽명, key, value);
//
            notificationResult.whenComplete((result, ex) -> { // 해당 토픽에 정상적으로 가면
                if (ex == null) {
                    log.info("결제 알림 이벤트 발행 성공 - 토픽: {}, 오프셋: {}, 파티션: {}",
                            notificationTopic, result.getRecordMetadata().offset(),
                            result.getRecordMetadata().partition());
                } else {
                    log.error("결제 알림 이벤트 발행 실패 - 토픽: {}, 고객ID: {}",
                            notificationTopic, event.getCustomerId(), ex);
                }
            });

            // 이상거래 탐지용 토픽으로 발행
            CompletableFuture<SendResult<String, Object>> anomalyResult =
                kafkaTemplate.send(anomalyDetectionTopic, event.getCustomerId().toString(), event);

            anomalyResult.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("결제 이상거래탐지 이벤트 발행 성공 - 토픽: {}, 오프셋: {}",
                            anomalyDetectionTopic, result.getRecordMetadata().offset());
                } else {
                    log.error("결제 이상거래탐지 이벤트 발행 실패 - 토픽: {}, 고객ID: {}",
                            anomalyDetectionTopic, event.getCustomerId(), ex);
                }
            });

        } catch (Exception e) {
            log.error("카드 결제 이벤트 발행 중 예상치 못한 오류 - 고객ID: {}", event.getCustomerId(), e);
        }
    }

    /**
     * 카드 결제 취소 완료 이벤트 발행
     */
    public void publishCancelEvent(CancelEvent event) {
        try {
            event.setEventType("CANCEL");
            log.info("카드 취소 이벤트 발행 시작 - 고객ID: {}, 거래번호: {}, 취소금액: {}",
                    event.getCustomerId(), event.getTransactionUniqueNo(), event.getCancelAmount());

            // 알림용 토픽으로 발행 (점주 알림)
            CompletableFuture<SendResult<String, Object>> notificationResult =
                kafkaTemplate.send(notificationTopic, event.getCustomerId().toString(), event);

            notificationResult.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("취소 알림 이벤트 발행 성공 - 토픽: {}, 오프셋: {}, 파티션: {}",
                            notificationTopic, result.getRecordMetadata().offset(),
                            result.getRecordMetadata().partition());
                } else {
                    log.error("취소 알림 이벤트 발행 실패 - 토픽: {}, 고객ID: {}",
                            notificationTopic, event.getCustomerId(), ex);
                }
            });

            // 이상거래 탐지용 토픽으로 발행
            CompletableFuture<SendResult<String, Object>> anomalyResult =
                kafkaTemplate.send(anomalyDetectionTopic, event.getCustomerId().toString(), event);

            anomalyResult.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("취소 이상거래탐지 이벤트 발행 성공 - 토픽: {}, 오프셋: {}",
                            anomalyDetectionTopic, result.getRecordMetadata().offset());
                } else {
                    log.error("취소 이상거래탐지 이벤트 발행 실패 - 토픽: {}, 고객ID: {}",
                            anomalyDetectionTopic, event.getCustomerId(), ex);
                }
            });

        } catch (Exception e) {
            log.error("카드 취소 이벤트 발행 중 예상치 못한 오류 - 고객ID: {}", event.getCustomerId(), e);
        }
    }

}