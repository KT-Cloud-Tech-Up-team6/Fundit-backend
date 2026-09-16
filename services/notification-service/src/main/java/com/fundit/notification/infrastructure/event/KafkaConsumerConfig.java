package com.fundit.notification.infrastructure.event;

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
 * 알림 구독에 필요한 빈 2개만 등록한다. 브로커 접속·group-id·역직렬화기는
 * {@code application.yml}의 {@code spring.kafka.*}와 Boot 자동구성이 처리하므로
 * ConsumerFactory/ContainerFactory를 직접 만들지 않는다.
 *
 * <p>발행 측 4개 서비스(order/payment/project/fulfillment)는 {@code KafkaConfig}에서
 * ProducerFactory까지 직접 만든다 — {@code KafkaTemplate<String,Object>} 제네릭이 자동구성 빈과
 * 매칭되지 않는 문제 때문이다. <b>notification은 소비 전용이라 그 사정이 없어</b> 자동구성을 그대로 쓴다.
 */
@Configuration
public class KafkaConsumerConfig {

    private static final String TRUSTED_PACKAGE = "com.fundit.*";

    /**
     * payload는 문자열로 받고(application.yml의 StringDeserializer) 타입 변환은 여기서 한다.
     * 타입을 {@code @KafkaListener} 메서드 파라미터로 <b>추론</b>하는 이유: 이벤트 레코드는 서비스마다
     * 따로 선언하므로(event-convention.md 4번) 발행 측이 실어 보낸 타입 헤더를 신뢰하면 안 된다.
     *
     * <p>{@code FAIL_ON_UNKNOWN_PROPERTIES}를 끄는 건 event-convention.md 6번
     * "소비자는 모르는 필드를 무시해야 한다" 요구다 — 발행 측이 {@code .v1}을 유지한 채 필드를
     * 추가해도 구독자가 깨지지 않아야 한다.
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
     *
     * <p>기본값은 {@code FixedBackOff(0L, 9)}라 같은 메시지를 10번 시도한다. 역직렬화 실패는
     * 결정적이라 재시도해도 결과가 같으므로 {@code FixedBackOff(0, 0)}으로 한 번만 시도한다
     * — 발행 측 4개 서비스와 같은 설정이다.
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(0L, 0L));
    }
}
