package com.fundit.search.infrastructure.event;

import org.apache.kafka.common.errors.SerializationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.ConversionException;
import org.springframework.kafka.support.converter.JacksonJsonMessageConverter;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.mapping.DefaultJacksonJavaTypeMapper;
import org.springframework.kafka.support.mapping.JacksonJavaTypeMapper;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * search-service는 소비 전용이라(발행하는 이벤트가 없음) notification-service와 동일하게
 * ConsumerFactory/ContainerFactory를 직접 만들지 않고 자동구성을 그대로 쓴다.
 * DLT 재발행만 {@link KafkaTemplate}을 쓰며, 도메인 이벤트는 발행하지 않는다.
 */
@Configuration
public class KafkaConsumerConfig {

    private static final String TRUSTED_PACKAGE = "com.fundit.*";

    /**
     * payload는 문자열로 받고(application.yml의 StringDeserializer) 타입 변환은 여기서 한다.
     * 타입을 {@code @KafkaListener} 메서드 파라미터로 추론하는 이유: 이벤트 레코드는 서비스마다
     * 따로 선언하므로(event-convention.md 4번) 발행 측이 실어 보낸 타입 헤더를 신뢰하면 안 된다.
     *
     * <p>{@code FAIL_ON_UNKNOWN_PROPERTIES}를 끄는 건 event-convention.md 6번
     * "소비자는 모르는 필드를 무시해야 한다" 요구다.
     */
    @Bean
    public RecordMessageConverter kafkaMessageConverter() {
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
     * 역직렬화·변환 실패는 재시도해도 결과가 같아 즉시 DLT로 보낸다. 리스너 처리 실패(색인 미도착 등
     * 일시적 오류)는 백오프로 재시도하고, 소진된 레코드는 기본 로깅 recoverer 대신 DLT로 보내
     * 나중에 재처리할 수 있게 한다(event-convention.md 7번).
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            KafkaTemplate<?, ?> kafkaTemplate,
            @Value("${search.kafka.retry.interval-ms:1000}") long intervalMs,
            @Value("${search.kafka.retry.max-attempts:4}") long maxAttempts) {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(kafkaTemplate), new FixedBackOff(intervalMs, maxAttempts));
        handler.addNotRetryableExceptions(
                DeserializationException.class,
                SerializationException.class,
                MessageConversionException.class,
                ConversionException.class);
        return handler;
    }
}
