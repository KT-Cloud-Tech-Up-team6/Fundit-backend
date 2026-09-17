package com.fundit.search.infrastructure.event;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.JacksonJsonMessageConverter;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.mapping.DefaultJacksonJavaTypeMapper;
import org.springframework.kafka.support.mapping.JacksonJavaTypeMapper;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * search-service는 소비 전용이라(발행하는 이벤트가 없음) notification-service와 동일하게
 * ConsumerFactory/ContainerFactory를 직접 만들지 않고 자동구성을 그대로 쓴다.
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
     * 실패한 메시지 하나가 파티션을 막지 않게 로그만 남기고 다음으로 넘어간다(event-convention.md 7번).
     * 역직렬화 실패는 결정적이라 재시도해도 결과가 같으므로 {@code FixedBackOff(0, 0)}으로 한 번만 시도한다.
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(0L, 0L));
    }
}
