package com.fundit.member.infrastructure.event;

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
 * 소비 측 빈. {@link KafkaProducerConfig}와 같은 이유로 Boot 자동구성 없이 직접 만든다 —
 * member-service는 스타터가 아니라 spring-kafka만 쓰므로 {@code spring.kafka.consumer.*} yml이 적용되지 않는다.
 *
 * <p>payload는 문자열로 받고 타입은 {@code @KafkaListener} 파라미터로 추론한다 — 이벤트 레코드는
 * 서비스마다 따로 선언하므로(event-convention.md 4번) 발행 측 타입 헤더를 믿지 않는다. 모르는 필드는
 * 무시한다(6번). search-service {@code KafkaConsumerConfig}와 같은 방식이다.
 */
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    private static final String GROUP_ID = "member-service";

    @Bean
    public ConsumerFactory<String, String> consumerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        return new DefaultKafkaConsumerFactory<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID,
                // 커밋된 오프셋이 없을 때만 적용된다. 스냅샷은 조회용이라 과거 이벤트를 건너뛰면 영구히 빈다.
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build());
        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();
        typeMapper.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);
        typeMapper.addTrustedPackages("com.fundit.*");
        converter.setTypeMapper(typeMapper);

        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setRecordMessageConverter(converter);
        // ponytail: 재시도 후에도 실패하면 로그만 남기고 넘어간다(DLT 없음). 스냅샷은 다음 수정 이벤트 때
        // 다시 채워지는 조회용 데이터라서다. 누락이 문제 되면 DeadLetterPublishingRecoverer를 붙인다.
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, 3L)));
        return factory;
    }
}
