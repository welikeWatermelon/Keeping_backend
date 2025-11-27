package com.ssafy.keeping.domain.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.keeping.domain.event.dto.PaymentEvent;
import com.ssafy.keeping.domain.event.dto.CancelEvent;
import com.ssafy.keeping.domain.notification.entity.NotificationType;
import com.ssafy.keeping.domain.notification.service.NotificationService;
import com.ssafy.keeping.domain.notification.service.FcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventConsumer {
    // 얘네는 카프카가 자동으로 호출해줌
    // 어떻게 알고?
    // @KafkaListener 이 어노테이션을 통해 topic과 groupId를 통해 특정해줌

    private final NotificationService notificationService;
    private final FcmService fcmService;
    private final ObjectMapper objectMapper;

    /**
     * 알림 이벤트 소비 (재시도 설정 포함)
     * 2초, 4초, 8초, 16초 간격으로 재시도 후 DLQ로 전송
     */
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @KafkaListener(topics = "notification-events", groupId = "keeping-service-group")
    public void handleNotificationEvent(
            ConsumerRecord<String, Object> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        try {
            log.info("알림 이벤트 수신 - 토픽: {}, 파티션: {}, 오프셋: {}, 키: {}",
                    topic, partition, offset, record.key());

            Object eventData = record.value();

            if (eventData instanceof Map) {
                Map<String, Object> eventMap = (Map<String, Object>) eventData;
                String eventType = (String) eventMap.get("eventType");

                if ("PAYMENT".equals(eventType)) {
                    handlePaymentEvent(eventMap);
                } else if ("CANCEL".equals(eventType)) {
                    handleCancelEvent(eventMap);
                } else {
                    log.warn("알 수 없는 이벤트 타입: {}", eventType);
                }
            } else {
                log.warn("예상치 못한 이벤트 데이터 타입: {}", eventData.getClass().getSimpleName());
            }

            // 수동으로 commit
            acknowledgment.acknowledge(); // 메시지 처리가 완료되었으니 kafka에게 알려줘야지~
            log.info("알림 이벤트 처리 완료 - 오프셋: {}", offset);

        } catch (Exception e) {
            log.error("알림 이벤트 처리 중 오류 - 토픽: {}, 오프셋: {}", topic, offset, e);
            throw e; // 재시도를 위해 예외를 다시 던짐
        }
    }

    /**
     * 결제 이벤트 처리 - 점주에게 알림
     */
    private void handlePaymentEvent(Map<String, Object> eventMap) {
        try {
            PaymentEvent event = objectMapper.convertValue(eventMap, PaymentEvent.class);

            log.info("결제 알림 이벤트 처리 - 고객: {}, 점주ID: {}, 금액: {}",
                    event.getCustomerName(), event.getOwnerId(), event.getPaymentAmount());

            // 점주에게 알림 전송 (기존 NotificationService 활용)
            String notificationContent = String.format("%s님이 %,d원을 결제했습니다 (포인트: %,d)",
                    event.getCustomerName(), event.getPaymentAmount(), event.getTotalPoints());

            notificationService.sendToOwner(
                    event.getOwnerId(),
                    NotificationType.POINT_CHARGE,
                    notificationContent
            );

            log.info("결제 알림 전송 완료 - 점주ID: {}", event.getOwnerId());

        } catch (Exception e) {
            log.error("결제 이벤트 처리 중 오류", e);
            throw e;
        }
    }

    /**
     * 취소 이벤트 처리 - 점주에게 알림
     */



    private void handleCancelEvent(Map<String, Object> eventMap) {
        try {
            CancelEvent event = objectMapper.convertValue(eventMap, CancelEvent.class);

            log.info("취소 알림 이벤트 처리 - 고객: {}, 점주ID: {}, 취소금액: {}",
                    event.getCustomerName(), event.getOwnerId(), event.getCancelAmount());

            // 점주에게 취소 알림 전송
            String notificationContent = String.format("%s님이 %,d원을 취소했습니다 (거래번호: %s)",
                    event.getCustomerName(), event.getCancelAmount(), event.getTransactionUniqueNo());

            notificationService.sendToOwner(
                    event.getOwnerId(),
                    NotificationType.PAYMENT_CANCELED,
                    notificationContent
            );

            log.info("취소 알림 전송 완료 - 점주ID: {}", event.getOwnerId());

        } catch (Exception e) {
            log.error("취소 이벤트 처리 중 오류", e);
            throw e;
        }
    }


    /**
     * DLQ 이벤트 처리
     */
    @KafkaListener(topics = "notification-events-dlq", groupId = "keeping-service-group-dlq")
    public void handleDlqEvent(
            ConsumerRecord<String, Object> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {

        try {
            log.warn("DLQ 이벤트 수신 - 토픽: {}, 키: {}, 재처리 실패한 이벤트",
                    topic, record.key());

            // 관리자에게 FCM 알림 (특정 customerId로 가정)
            String title = "시스템 알림 처리 실패";
            String body = String.format("알림 이벤트 처리에 실패했습니다. 수동 확인이 필요합니다. (키: %s)", record.key());

            Map<String, String> fcmData = Map.of(
                "type", "DLQ_NOTIFICATION",
                "failedKey", record.key() != null ? record.key() : "unknown",
                "topic", topic
            );

             fcmService.sendToCustomer(1L, NotificationType.DLQ_NOTICE, title, body, fcmData);

            log.warn("DLQ 이벤트 처리 완료 - 관리자 알림 전송");
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("DLQ 이벤트 처리 중 오류", e);
            acknowledgment.acknowledge(); // DLQ에서는 실패해도 ack
        }
    }
}