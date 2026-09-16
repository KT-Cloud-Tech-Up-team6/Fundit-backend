package com.fundit.member.infrastructure.event;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.Map;

/**
 * Boot의 Kafka 자동구성(spring.kafka.* yml 프로퍼티 기반)에 기대지 않고 직접 빈을 만든다 —
 * 정확히 {@code KafkaTemplate<String, Object>} 타입으로 주입받아야 하는데 제네릭 불일치로
 * 자동구성 빈이 매칭되지 않는다. 발행 측 4개 서비스(project/order/payment/fulfillment)의
 * {@code KafkaProducerConfig}와 같은 형태다.
 *
 * <p>{@code bootstrap-servers}는 테스트·local에서 기본값(localhost:9092)을 쓰고,
 * dev/prod는 application-{profile}.yml의 {@code ${KAFKA_BOOTSTRAP_SERVERS}}가 덮어쓴다.
 */
@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, Object> producerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all");
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
