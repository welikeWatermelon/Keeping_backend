package com.ssafy.keeping.domain.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.keeping.domain.event.dto.CancelEvent;
import com.ssafy.keeping.domain.event.dto.PaymentEvent;
import com.ssafy.keeping.domain.notification.entity.NotificationType;
import com.ssafy.keeping.domain.notification.service.FcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnomalyDetectionConsumer {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final FcmService fcmService;

    private int dailyPaymentThreshold = 20;

    private int dailyCancelThreshold = 30;

    private static final String PAYMENT_KEY_PREFIX = "anomaly:payment:";
    private static final String CANCEL_KEY_PREFIX = "anomaly:cancel:";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 이상거래 탐지 이벤트 소비
     */
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @KafkaListener(topics = "anomaly-detection-events", groupId = "keeping-service-group-anomaly")
    public void handleAnomalyDetectionEvent(
            ConsumerRecord<String, Object> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        try {
            log.info("이상거래 탐지 이벤트 수신 - 토픽: {}, 파티션: {}, 오프셋: {}, 키: {}",
                    topic, partition, offset, record.key());

            Object eventData = record.value();

            if (eventData instanceof Map) {
                Map<String, Object> eventMap = (Map<String, Object>) eventData;
                String eventType = (String) eventMap.get("eventType");

                if ("PAYMENT".equals(eventType)) {
                    handlePaymentAnomalyDetection(eventMap);
                } else if ("CANCEL".equals(eventType)) {
                    handleCancelAnomalyDetection(eventMap);
                } else {
                    log.warn("이상거래 탐지: 알 수 없는 이벤트 타입 - {}", eventType);
                }
            } else {
                log.warn("이상거래 탐지: 예상치 못한 이벤트 데이터 타입 - {}",
                        eventData != null ? eventData.getClass().getSimpleName() : "null");
            }

            acknowledgment.acknowledge();
            log.info("이상거래 탐지 이벤트 처리 완료 - 오프셋: {}", offset);

        } catch (Exception e) {
            log.error("이상거래 탐지 이벤트 처리 중 오류 - 토픽: {}, 오프셋: {}", topic, offset, e);
            throw e;
        }
    }

    /**
     * 결제 이상거래 탐지 처리
     */
    private void handlePaymentAnomalyDetection(Map<String, Object> eventMap) {
        try {
            PaymentEvent event = objectMapper.convertValue(eventMap, PaymentEvent.class);

            String today = LocalDate.now().format(DATE_FORMATTER);
            String redisKey = PAYMENT_KEY_PREFIX + event.getCustomerId() + ":" + today;

            log.info("Redis 키 생성: {}", redisKey);

            // Redis에서 오늘 결제 횟수 증가
            Long currentCount = redisTemplate.opsForValue().increment(redisKey);

            log.info("Redis INCR 실행 완료: {} = {}", redisKey, currentCount);

            // 첫 번째 증가인 경우 TTL 설정 (24시간)
            if (currentCount == 1) {
                redisTemplate.expire(redisKey, 24, TimeUnit.HOURS);
            }

            log.info("결제 이상거래 탐지 - 고객ID: {}, 오늘 결제 횟수: {}/{}",
                    event.getCustomerId(), currentCount, dailyPaymentThreshold);

            // 임계치 초과 시 직접 FCM 알림 전송 (첫 번째 초과 시점에만)
            if (currentCount == dailyPaymentThreshold + 1) {
                sendAnomalyFcmNotification(event.getCustomerId(), "EXCESSIVE_PAYMENT",
                        currentCount.intValue(), dailyPaymentThreshold);
            }

        } catch (Exception e) {
            log.error("결제 이상거래 탐지 처리 중 오류", e);
            throw e;
        }
    }

    /**
     * 취소 이상거래 탐지 처리
     */
    private void handleCancelAnomalyDetection(Map<String, Object> eventMap) {
        try {
            CancelEvent event = objectMapper.convertValue(eventMap, CancelEvent.class);

            String today = LocalDate.now().format(DATE_FORMATTER);
            String redisKey = CANCEL_KEY_PREFIX + event.getCustomerId() + ":" + today;

            // Redis에서 오늘 취소 횟수 증가
            Long currentCount = redisTemplate.opsForValue().increment(redisKey);

            // 첫 번째 증가인 경우 TTL 설정 (24시간)
            if (currentCount == 1) {
                redisTemplate.expire(redisKey, 24, TimeUnit.HOURS);
            }

            log.info("취소 이상거래 탐지 - 고객ID: {}, 오늘 취소 횟수: {}/{}",
                    event.getCustomerId(), currentCount, dailyCancelThreshold);

            // 임계치 초과 시 직접 FCM 알림 전송 (첫 번째 초과 시점에만)
            if (currentCount == dailyCancelThreshold + 1) {
                sendAnomalyFcmNotification(event.getCustomerId(), "EXCESSIVE_CANCEL",
                        currentCount.intValue(), dailyCancelThreshold);
            }

        } catch (Exception e) {
            log.error("취소 이상거래 탐지 처리 중 오류", e);
            throw e;
        }
    }

    /**
     * 이상거래 탐지 시 FCM 직접 알림 전송
     */
    private void sendAnomalyFcmNotification(Long customerId, String anomalyType, int dailyCount, int threshold) {
        try {
            log.warn("이상거래 감지! 고객ID: {}, 유형: {}, 횟수: {}/{}",
                    customerId, anomalyType, dailyCount, threshold);

            // FCM으로 직접 알림 전송
            String title = "이상거래 탐지 알림";
            String body;

            if ("EXCESSIVE_PAYMENT".equals(anomalyType)) {
                body = String.format("오늘 %d건의 결제가 발생했습니다. (기준: %d건)", dailyCount, threshold);
            } else if ("EXCESSIVE_CANCEL".equals(anomalyType)) {
                body = String.format("오늘 %d건의 결제 취소가 발생했습니다. (기준: %d건)", dailyCount, threshold);
            } else {
                body = "비정상적인 거래 패턴이 감지되었습니다.";
            }

            Map<String, String> fcmData = Map.of(
                "anomalyType", anomalyType,
                "dailyCount", String.valueOf(dailyCount),
                "threshold", String.valueOf(threshold),
                "date", LocalDate.now().format(DATE_FORMATTER)
            );

            fcmService.sendToCustomer(1L, NotificationType.ANOMALY_DETECTED, title, body, fcmData);

            log.warn("이상거래 FCM 알림 전송 완료 - customerId: {}, anomalyType: {}", customerId, anomalyType);

        } catch (Exception e) {
            log.error("이상거래 FCM 알림 전송 중 오류 - 고객ID: {}", customerId, e);
        }
    }

    /**
     * DLQ 이벤트 처리
     */
    @KafkaListener(topics = "anomaly-detection-events-dlq", groupId = "keeping-service-group-anomaly-dlq")
    public void handleDlqEvent(
            ConsumerRecord<String, Object> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {

        try {
            log.warn("이상거래 탐지 DLQ 이벤트 수신 - 토픽: {}, 키: {}", topic, record.key());

            // DLQ에 들어온 이벤트는 수동 처리나 관리자 확인이 필요
            // 실제 운영에서는 별도 모니터링 시스템에 알림 전송
            log.error("이상거래 탐지 처리 실패로 DLQ 전송됨 - 수동 확인 필요, 키: {}", record.key());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("이상거래 탐지 DLQ 이벤트 처리 중 오류", e);
            acknowledgment.acknowledge(); // DLQ에서는 실패해도 ack
        }
    }
}