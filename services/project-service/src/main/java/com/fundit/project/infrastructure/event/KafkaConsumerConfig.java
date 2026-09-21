package com.fundit.project.infrastructure.event;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.JacksonJsonMessageConverter;
import org.springframework.kafka.support.mapping.DefaultJacksonJavaTypeMapper;
import org.springframework.kafka.support.mapping.JacksonJavaTypeMapper;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * PROJECT-016(찜 통계) — 이 서비스 최초의 Kafka 컨슈머 배선. {@code KafkaProducerConfig}는
 * 발행 전용이라 컨슈머 팩토리/메시지 컨버터가 없었다(order-service {@code KafkaConfig}의
 * 컨슈머 절반과 동일 패턴 — event-convention.md 배선 공통 설정).
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    private static final String TRUSTED_PACKAGE = "com.fundit.*";

    @Bean
    public ConsumerFactory<String, String> consumerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "project-service",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * payload는 문자열로만 받고(consumerFactory), 실제 타입 변환은 이 메시지 컨버터가
     * {@code @KafkaListener} 메서드 파라미터 타입으로 추론해서 한다 — 이벤트 레코드는 서비스마다
     * 따로 선언되므로(event-convention.md 4번) 발행 측 타입 헤더를 신뢰하지 않고 항상 추론한다.
     * {@code FAIL_ON_UNKNOWN_PROPERTIES}를 꺼서 모르는 필드(eventId 등)가 와도 깨지지 않는다
     * (event-convention.md 6번 "소비자는 모르는 필드를 무시해야 한다").
     */
    @Bean
    public JacksonJsonMessageConverter kafkaMessageConverter() {
        JsonMapper jsonMapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();
        typeMapper.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);
        typeMapper.addTrustedPackages(TRUSTED_PACKAGE);
        converter.setTypeMapper(typeMapper);
        return converter;
    }

    /**
     * 역직렬화/처리 실패 메시지 하나가 파티션 전체를 막지 않도록 재시도 없이(FixedBackOff(0,0))
     * 로그만 남기고 다음 메시지로 넘어간다(event-convention.md 7번).
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory, JacksonJsonMessageConverter kafkaMessageConverter) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setRecordMessageConverter(kafkaMessageConverter);
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(0L, 0L)));
        return factory;
    }
}
