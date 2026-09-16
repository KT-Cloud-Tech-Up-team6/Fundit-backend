package com.fundit.member.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link WishEventTransport}의 Kafka 구현체.
 *
 * <p>payload는 봉투 없이 평평한 JSON이다 — {@link WishEvent} 레코드가 그대로 직렬화되고
 * {@code eventId}는 그 필드 중 하나다(event-convention.md 4·5번).
 *
 * <p><b>파티션 키는 memberId다.</b> 순서가 필요한 건 한 회원이 같은 프로젝트를 빠르게
 * 찜→해제하는 경우고, 뒤집히면 소비 측에서 지울 행이 없어 카운트가 안 내려간 채 뒤이은
 * wished가 +1 해서 찜하지 않은 회원이 통계에 남는다.
 */
@Component
@RequiredArgsConstructor
public class KafkaWishEventTransport implements WishEventTransport {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void sendWished(WishEvent event) {
        kafkaTemplate.send(KafkaTopics.PROJECT_WISHED, event.memberId().toString(), event);
    }

    @Override
    public void sendUnwished(WishEvent event) {
        kafkaTemplate.send(KafkaTopics.PROJECT_UNWISHED, event.memberId().toString(), event);
    }
}
