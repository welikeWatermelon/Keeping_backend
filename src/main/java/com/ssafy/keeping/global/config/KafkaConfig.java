package com.ssafy.keeping.global.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConfig {

//    private String bootstrapServers = "localhost:9092";
    private String bootstrapServers = "kafka:9092";

    private String groupId = "keeping-service-group";

    private String notificationTopicName = "notification-events";

    private String anomalyDetectionTopicName = "anomaly-detection-events";

    private String notificationDlqTopicName = "notification-events-dlq";

    private String anomalyDetectionDlqTopicName = "anomaly-detection-events-dlq";

    // ==================== Admin Configuration ====================

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    // ==================== Topic Configuration ====================

    @Bean
    public NewTopic notificationEventsTopic() {
        return TopicBuilder.name(notificationTopicName)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic anomalyDetectionEventsTopic() {
        return TopicBuilder.name(anomalyDetectionTopicName)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic notificationEventsDlqTopic() {
        return TopicBuilder.name(notificationDlqTopicName)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic anomalyDetectionEventsDlqTopic() {
        return TopicBuilder.name(anomalyDetectionDlqTopicName)
                .partitions(1)
                .replicas(1)
                .build();
    }

    // ==================== Producer Configuration ====================

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // 성능 및 신뢰성 설정
        configProps.put(ProducerConfig.ACKS_CONFIG, "1"); // 리더 파티션에서 응답 후 성공
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);

//        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ==================== Consumer Configuration ====================

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // 오프셋 설정
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // JSON Deserializer 설정
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.ssafy.keeping");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "java.util.Map");

        // 수동 커밋 설정
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // 세션 및 하트비트 설정
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);

        // 배치 처리 설정
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());

        // 수동 commit 설정
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // 에러 핸들링
        factory.setCommonErrorHandler(new org.springframework.kafka.listener.DefaultErrorHandler());

        // 동시성 설정 (EC2 단일 서버이므로 적은 수로 설정)
        factory.setConcurrency(1);

        return factory;
    }

    // ==================== Anomaly Detection Consumer Factory ====================

    @Bean("anomalyConsumerFactory")
    public ConsumerFactory<String, Object> anomalyConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-anomaly");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // 오프셋 설정
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // JSON Deserializer 설정
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.ssafy.keeping");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "java.util.Map");

        // 수동 커밋 설정
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean("anomalyKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> anomalyKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(anomalyConsumerFactory());

        // 수동 commit 설정
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // 에러 핸들링
        factory.setCommonErrorHandler(new org.springframework.kafka.listener.DefaultErrorHandler());

        // 동시성 설정
        factory.setConcurrency(1);

        return factory;
    }

    // ==================== DLQ Consumer Factory ====================

    @Bean("dlqConsumerFactory")
    public ConsumerFactory<String, Object> dlqConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-dlq");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // 오프셋 설정
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // JSON Deserializer 설정
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.ssafy.keeping");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "java.util.Map");

        // 수동 커밋 설정
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean("dlqKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> dlqKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(dlqConsumerFactory());

        // 수동 commit 설정
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // DLQ에서는 에러 핸들러 최소화
        factory.setCommonErrorHandler(new org.springframework.kafka.listener.DefaultErrorHandler());

        // 동시성 설정
        factory.setConcurrency(1);

        return factory;
    }
}